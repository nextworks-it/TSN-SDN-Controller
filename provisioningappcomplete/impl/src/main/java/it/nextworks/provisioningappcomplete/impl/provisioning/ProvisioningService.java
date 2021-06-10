package it.nextworks.provisioningappcomplete.impl.provisioning;

import it.nextworks.provisioningappcomplete.impl.provisioning.exceptions.PathComputationException;
import it.nextworks.provisioningappcomplete.impl.provisioning.exceptions.ProvisioningException;
import it.nextworks.provisioningappcomplete.impl.tapi.TapiDataStoreManager;
import it.nextworks.provisioningappcomplete.impl.tapi.TapiTopologyBuilder;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.yang.gen.v1.http.www.advaoptical.com.ns.yang.fsp150cm.common.rev200310.VlanTag;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.Uuid;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.global._class.Name;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.tapi.context.ServiceInterfacePoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.CreateConnectivityServiceInput;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connection.*;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.context.Connection;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.context.ConnectivityService;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.context.ConnectivityContext;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.create.connectivity.service.input.EndPoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.eth.rev200423.*;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.path.computation.rev200423.path.computation.context.Path;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.link.NodeEdgePoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.context.Topology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ProvisioningService {
    private static final Logger LOG = LoggerFactory.getLogger(ProvisioningService.class);
    private DataBroker dataBroker;
    private String pceIp;
    private String pcePort;
    private MountPointService mps;

    public ProvisioningService(DataBroker db, MountPointService mps, String pceIp, String pcePort){
        this.dataBroker = db;
        this.pceIp = pceIp;
        this.pcePort = pcePort;
        this.mps = mps;
    }

    // Connectivity Service creation logic
    // 1. Check parameter in the request, for example VLAN info and if SIPs exist in the topology
    // 2. Interaction with PCE to compute and retrieve the path
    // 3. Configure the device in the path
    // 4. Update the topology creating CEPs over NEPs
    //      and update the connectivity-context, creating the connectivity service and the connection
    public ConnectivityService createConnectivityService(CreateConnectivityServiceInput input) throws ProvisioningException, PathComputationException {
        // check VLAN params  and SIPs info
        String VLANID = checkVLAN(input);
        ArrayList<String> sipList = checkSIPs(input);

        // Compute the path
        PCEClient pceClient = new PCEClient(pceIp,pcePort);
        Path path = pceClient.createP2PPath(sipList.get(0), sipList.get(1));
        if (path == null){
            throw new PathComputationException("Error computing path");
        }

        // UPDATE TOPOLOGY
        Topology topology = TapiDataStoreManager.readTopology(dataBroker);
        // Retrieve all the traversed NEPs using the computed PATH, the internal ones are retrieved from the traversed links
        // the "edge" NEPs are added retrieving them from the SIPs
        ArrayList<NodeEdgePoint> traversedNEPs = PathComputationUtils.buildTraversedNEPList(topology,path,sipList);
        TapiTopologyBuilder topologyBuilder = new TapiTopologyBuilder(topology);
        // Create a CEP over each traversed NEP in the Topology and update it. It returns also the entire list of created CEPs
        ArrayList<ConnectionEndPoint> connCepList = topologyBuilder.addCEPsToTopologyNodes(traversedNEPs);
        TapiDataStoreManager.writeTopology(dataBroker,topologyBuilder.getTopology());

        // UPDATE CONNECTIVITY-CONTEXT
        // Retrieve the actual connectivity-context, update it adding new connections and connectivity-service and store it
        for(ConnectionEndPoint cep: connCepList){
            LOG.info("new CEP "+cep.getConnectionEndPointUuid().getValue());
        }
        Uuid csUuidString = new Uuid(UUID.randomUUID().toString());
        Uuid topLevelConnectionUuid = new Uuid(UUID.randomUUID().toString());
        List<Connection> connections = TapiConnectivityContextUtils.createConnections(connCepList,topLevelConnectionUuid);
        ConnectivityService cs = TapiConnectivityContextUtils.createConnectivityService(input,topLevelConnectionUuid,csUuidString,path.getUuid().getValue());

        ConnectivityContext cctx = TapiDataStoreManager.readConnectivityContext(dataBroker);
        TapiConnectivityContextBuilder builder = new TapiConnectivityContextBuilder(cctx);
        builder.addConnectivityService(cs).addConnections(connections);
        TapiDataStoreManager.writeConnectivityContext(dataBroker, builder.getConnectivityContext());

        // DEVICE CONFIGURATION
        VLANProvisioning vlanProvisioning = new VLANProvisioning(mps,traversedNEPs,VLANID);
        vlanProvisioning.createVLAN();
        // TODO if something goes wrong delete the CS?
        return cs;
    }

    private String checkVLAN(CreateConnectivityServiceInput input) throws ProvisioningException {
        // Check VLAN params
        if(input.getEndPoint()== null || input.getEndPoint().size()!=2){
            throw new ProvisioningException("Connectivity-Service creation request must contain 2 endpoints");
        }
        EndPoint ep1 = input.getEndPoint().get(0);
        EndPoint ep2 = input.getEndPoint().get(1);
        EndPoint2 ethAugm1 = ep1.augmentation(EndPoint2.class);
        EndPoint2 ethAugm2 = ep2.augmentation(EndPoint2.class);

        if (ethAugm1==null || ethAugm1.getEthConnectivityServiceEndPointSpec() == null ||
                ethAugm1.getEthConnectivityServiceEndPointSpec().getEthTerminationCommonPac() == null ||
                ethAugm1.getEthConnectivityServiceEndPointSpec().getEthTerminationCommonPac().getPortVid()==null ||
                ethAugm2==null || ethAugm2.getEthConnectivityServiceEndPointSpec() == null ||
                ethAugm2.getEthConnectivityServiceEndPointSpec().getEthTerminationCommonPac() == null ||
                ethAugm2.getEthConnectivityServiceEndPointSpec().getEthTerminationCommonPac().getPortVid()==null) {
            throw new ProvisioningException("Connectivity-Service creation request missing VLAN info");
        }
        String vlanId1 = ethAugm1.getEthConnectivityServiceEndPointSpec().getEthTerminationCommonPac().getPortVid().getValue();
        String vlanId2 = ethAugm2.getEthConnectivityServiceEndPointSpec().getEthTerminationCommonPac().getPortVid().getValue();
        if(!vlanId1.equals(vlanId2)){
            throw new ProvisioningException("Connectivity-Service creation request endpoints should declare the same VLAN ID");
        }
        return vlanId1;
    }

    private ArrayList<String> checkSIPs(CreateConnectivityServiceInput input) throws ProvisioningException {
        // Check SIPs
        ArrayList<String> sips = new ArrayList<>();     // output containing 2 sips, src and dest
        String srcSIP;
        String dstSIP;

        if(input.getEndPoint()== null || input.getEndPoint().size()!=2){
            throw new ProvisioningException("Connectivity-Service creation request must contain 2 endpoints");
        }

        // Retrieve srcSIP & dstSIP
        srcSIP = input.getEndPoint().get(0).getServiceInterfacePoint().getServiceInterfacePointUuid().getValue();
        dstSIP = input.getEndPoint().get(1).getServiceInterfacePoint().getServiceInterfacePointUuid().getValue();

        // Retrieve context and check that they exist
        List<ServiceInterfacePoint> sipList = TapiDataStoreManager.readSIPList(dataBroker);
        if(!checkSIPs(sipList,srcSIP,dstSIP)){
            throw new ProvisioningException("Connectivity-Service creation request contains non-existent SIP or SIPs");
        }
        sips.add(srcSIP);
        sips.add(dstSIP);
        return sips;
    }

    private boolean checkSIPs(List<ServiceInterfacePoint> sipList, String srcSIP, String dstSIP){
        boolean srcSIPexists = false;
        boolean dstSIPexists = false;

        for(ServiceInterfacePoint sip : sipList){
            if(sip.getUuid().getValue().equals(srcSIP)){
                LOG.info("ProvisioningService - createConnectivityService: source SIP exists");
                srcSIPexists = true;
            }
            if(sip.getUuid().getValue().equals(dstSIP)){
                LOG.info("ProvisioningService - createConnectivityService: destination SIP exists");
                dstSIPexists = true;
            }
        }
        return (srcSIPexists && dstSIPexists);
    }


    public void deleteConnectivityService(String csUuid) throws ProvisioningException, PathComputationException {
        // check if the connectivity-service exists, before removing it
        if(!TapiDataStoreManager.existConnectivityService(dataBroker,csUuid))
            throw new ProvisioningException("Connectivity-service with UUID "+csUuid+" does not exist");

        // UPDATE CONNECTIVITY-CONTEXT
        // Retrieve connectivity-context, inizialize the TAPI Connectivity-Context builder
        // Delete connectivity-service with uuid = csUuid and retrieve it
        // Retrieve the top level connection and delete it from the connection list of the connectivity-context
        // Retrieve the Delete low level connections from the top-level conn. and delete them
        // Store the new connectivity-context
        ConnectivityContext connectivityContext = TapiDataStoreManager.readConnectivityContext(dataBroker);
        TapiConnectivityContextBuilder contextBuilder = new TapiConnectivityContextBuilder(connectivityContext);
        ConnectivityService removedCs = contextBuilder.removeConnectivityService(csUuid);
        Uuid topConnectionUuid = removedCs.getConnection().get(0).getConnectionUuid();
        Connection topConnection = contextBuilder.removeConnection(topConnectionUuid);
        for(LowerConnection lwc: topConnection.getLowerConnection()){
            contextBuilder.removeConnection(lwc.getConnectionUuid());
        }
        TapiDataStoreManager.writeConnectivityContext(dataBroker,contextBuilder.getConnectivityContext());

        // UPDATE TOPOLOGY-CONTEXT
        // Delete all CEP used in this CS: for this step, use the ROUTE field of the topConnection
        // which contains, as first element, the list (unordered) of all used CEPs.
        Topology topology = TapiDataStoreManager.readTopology(dataBroker,topConnection.getConnectionEndPoint().get(0).getTopologyUuid().getValue());
        TapiTopologyBuilder topologyBuilder = new TapiTopologyBuilder(topology);
        topologyBuilder.removeCEPsToTopologyNodes(topConnection.getRoute().get(0).getConnectionEndPoint());
        TapiDataStoreManager.writeTopology(dataBroker,topologyBuilder.getTopology());

        // DEVICE CONFIGURATION
        // Retrive the pathID from connectivity-service, retrive the path with pathID from PCE and compute of traversed NEPs
        String pathID = TapiConnectivityContextUtils.searchPathID(removedCs.getName());
        LOG.info("deleteConnectivityService - PathID "+pathID);
        if (pathID == null){
            throw new PathComputationException("Path not found in connectivity-service");
        }
        PCEClient pceClient = new PCEClient(pceIp,pcePort);
        Path path = pceClient.getP2PPath(pathID);
        if (path == null){
            throw new PathComputationException("Path not found from PCE");
        }
        // Retrieve all the traversed NEPs using the computed PATH, the internal ones are retrieved from the traversed links
        // the "edge" NEPs are added retrieving them from the SIPs
        ArrayList<String> sips = new ArrayList<>();
        sips.add(removedCs.getEndPoint().get(0).getServiceInterfacePoint().getServiceInterfacePointUuid().getValue());
        sips.add(removedCs.getEndPoint().get(1).getServiceInterfacePoint().getServiceInterfacePointUuid().getValue());
        ArrayList<NodeEdgePoint> traversedNEPs = PathComputationUtils.buildTraversedNEPList(topology,path,sips);

        String VLANID = getVLAN(removedCs.getEndPoint().get(0));
        VLANProvisioning vlanProvisioning = new VLANProvisioning(mps,traversedNEPs,VLANID);
        vlanProvisioning.deleteVLAN();
    }


    private String getVLAN(org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.service.EndPoint ep)
            throws ProvisioningException {
        EndPoint1 ethAugm1 = ep.augmentation(EndPoint1.class);

        if (ethAugm1==null || ethAugm1.getEthConnectivityServiceEndPointSpec() == null ||
                ethAugm1.getEthConnectivityServiceEndPointSpec().getEthTerminationCommonPac() == null ||
                ethAugm1.getEthConnectivityServiceEndPointSpec().getEthTerminationCommonPac().getPortVid()==null) {
            throw new ProvisioningException("Connectivity-Service endpoint missing VLAN info");
        }
        String vlanId1 = ethAugm1.getEthConnectivityServiceEndPointSpec().getEthTerminationCommonPac().getPortVid().getValue();
        return vlanId1;
    }


}
