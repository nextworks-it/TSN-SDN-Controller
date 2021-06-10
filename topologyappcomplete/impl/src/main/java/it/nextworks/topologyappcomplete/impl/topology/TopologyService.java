package it.nextworks.topologyappcomplete.impl.topology;

import it.nextworks.topologyappcomplete.impl.netconf.NetconfInfoUtils;
import it.nextworks.topologyappcomplete.impl.netconf.TopologyNetconfManager;
import it.nextworks.topologyappcomplete.impl.tapi.TapiDataStoreManager;
import it.nextworks.topologyappcomplete.impl.tapi.TapiTopologyBuilder;
import it.nextworks.topologyappcomplete.impl.topology.exceptions.TopologyCreationException;
import it.nextworks.topologyappcomplete.impl.topology.exceptions.TopologyRemovalException;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.it.nextworks.yang.topologyappcomplete.rev200514.AddStaticTopologyInput;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.PortDirection;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.tapi.context.ServiceInterfacePoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.tapi.context.ServiceInterfacePointBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.node.OwnedNodeEdgePoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.node.edge.point.MappedServiceInterfacePoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.Node;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.context.Topology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TopologyService {
    private static final Logger LOG = LoggerFactory.getLogger(TopologyService.class);
    private DataBroker dataBroker;
    private String netconfUsername;
    private String netconfPsw;

    public TopologyService(DataBroker db, String netconfUsername, String netconfPsw){
        this.dataBroker = db;
        this.netconfUsername = netconfUsername;
        this.netconfPsw = netconfPsw;
    }

    public void createStaticTopology(AddStaticTopologyInput input) throws TopologyCreationException {
        // check if the topology already exists
        String tapiTopologyUuid = input.getTopology().getUuid().getValue();
        if(TapiDataStoreManager.existTopology(dataBroker, tapiTopologyUuid)){
            throw new TopologyCreationException("Topology with UUID "+tapiTopologyUuid+" already exists");
        } else {
            List<Node> nodeList = input.getTopology().getNode();
            // Check if every node in the TAPI topology in input has mngm-ip and mngm-port, in order to create netconf connectors
            if (!NetconfInfoUtils.checkManagementInfo(nodeList))
                throw new TopologyCreationException("Missing NETCONF info on some nodes");
            else {
                // Creating netconf-connector for each node
                if(!createNetconfConnectors(nodeList))
                    throw new TopologyCreationException("Error creating netconf-connectors");

                // Creating and storing TAPI topology
                TapiTopologyBuilder tapiTopologyBuilder = new TapiTopologyBuilder(input.getTopology());
                if (!TapiDataStoreManager.writeTopology(dataBroker, tapiTopologyBuilder.getTopology()))
                    throw new TopologyCreationException("Error creating TAPI topology");

                // parsing all SIPs on nodes and add them to the TAPI context
                if(!storeSIPinContext(nodeList))
                    throw new TopologyCreationException("Error storing SIPs in Context");
            }
        }
    }

    private boolean createNetconfConnectors(List<Node> nodeList){
        for (Node node : nodeList) {
            String nodeUuidString = node.getUuid().getValue();
            String mngAddr = NetconfInfoUtils.getManagementAddress(node.getName());
            String mngPort = NetconfInfoUtils.getManagementPort(node.getName());
            if (!TopologyNetconfManager.createNetconfConnector(dataBroker, nodeUuidString, mngAddr, mngPort, netconfUsername, netconfPsw)) {
                LOG.error("TopologyService - createNetconfConnectors: Error creating netconf-connector for node "+nodeUuidString);
                return false;
            }
        }
        return true;
    }

    // Add SIPs to the list
    private boolean storeSIPinContext(List<Node> nodeList){
        ArrayList<ServiceInterfacePoint> sipList = new ArrayList<>();
        for(Node node : nodeList) {
            for (OwnedNodeEdgePoint onep : node.getOwnedNodeEdgePoint()) {
                if (onep.getMappedServiceInterfacePoint()!=null && !onep.getMappedServiceInterfacePoint().isEmpty()) {
                    for (MappedServiceInterfacePoint msip : onep.getMappedServiceInterfacePoint()) {
                        ServiceInterfacePointBuilder sipBuilder = new ServiceInterfacePointBuilder()
                                .setDirection(PortDirection.BIDIRECTIONAL)
                                .setUuid(msip.getServiceInterfacePointUuid());
                        sipList.add(sipBuilder.build());
                    }
                }
            }
        }
        if(!sipList.isEmpty()){
            if(TapiDataStoreManager.writeSIPList(dataBroker,sipList))
                return true;
            else return false;
        }
        return true;
    }

    public void deleteStaticTopology(String topologyUuid) throws TopologyRemovalException {
        // Retrieve the topology with the name specified in the input, if exists
        Topology tapiTopology = TapiDataStoreManager.readTopology(dataBroker,topologyUuid);
        if(tapiTopology == null)
            throw new TopologyRemovalException("TAPI Topology with UUID "+topologyUuid+" does not exist");

        // For each node, check if a netconf-connector exists with this name and delete it
        for(Node node : tapiTopology.getNode()){
            String nodeUuid = node.getUuid().getValue();
            TopologyNetconfManager.deleteNetconfConnector(dataBroker,nodeUuid);
        }
        ArrayList<String> sipsToBeRemoved = new ArrayList<>();
        // For each SIP, found in the topology, delete it in context
        for(Node node: tapiTopology.getNode()){
            for(OwnedNodeEdgePoint onep: node.getOwnedNodeEdgePoint()){
                // if SIPs on NEP exist
                if(onep.getMappedServiceInterfacePoint() != null) {
                    for (MappedServiceInterfacePoint msip : onep.getMappedServiceInterfacePoint()) {
                        sipsToBeRemoved.add(msip.getServiceInterfacePointUuid().getValue());
                    }
                }
            }
        }
        LOG.info("deleteStaticTopology - sips to be removed "+sipsToBeRemoved.size());
        if(sipsToBeRemoved.size()>0)
            removeSips(sipsToBeRemoved);

        // Delete the TAPI topology
        if(!TapiDataStoreManager.deleteTopology(dataBroker,topologyUuid))
            throw new TopologyRemovalException("Error deleting TAPI topology");
    }

    private void removeSips(ArrayList<String> sipsToBeRemoved){
        List<ServiceInterfacePoint> sipList = TapiDataStoreManager.readSIPList(dataBroker);
        if(sipList != null){
            for(String sipToBeRemoved: sipsToBeRemoved){
                Iterator iterator = sipList.iterator();
                while (iterator.hasNext()) {
                    ServiceInterfacePoint sip = (ServiceInterfacePoint)iterator.next();
                    if (sip.getUuid().getValue().equals(sipToBeRemoved)) {
                        iterator.remove();
                        LOG.info("deleteStaticTopology - Removed sip "+sipToBeRemoved);
                        break;
                    }
                }
            }
        } else {
            LOG.warn("deleteStaticTopology - no SIPs stored");
        }
        TapiDataStoreManager.writeSIPList(dataBroker,sipList);
    }
}
