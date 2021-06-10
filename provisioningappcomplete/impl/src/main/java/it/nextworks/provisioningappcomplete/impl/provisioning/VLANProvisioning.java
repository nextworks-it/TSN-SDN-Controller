package it.nextworks.provisioningappcomplete.impl.provisioning;

import com.google.common.collect.Iterables;
import it.nextworks.provisioningappcomplete.impl.provisioning.adva.AdvaEthernetPortBuilder;
import it.nextworks.provisioningappcomplete.impl.provisioning.adva.AdvaFSP150XG400Driver;
import it.nextworks.provisioningappcomplete.impl.provisioning.exceptions.ProvisioningException;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.yang.gen.v1.http.www.advaoptical.com.ns.yang.fsp150cm.common.rev200310.AdminState;
import org.opendaylight.yang.gen.v1.http.www.advaoptical.com.ns.yang.fsp150cm.common.rev200310.LearningType;
import org.opendaylight.yang.gen.v1.http.www.advaoptical.com.ns.yang.fsp150cm.common.rev200310.VlanTag;
import org.opendaylight.yang.gen.v1.http.www.advaoptical.com.ns.yang.fsp150cm.facility.rev200710.l2.flow.info.group.Flow;
import org.opendaylight.yang.gen.v1.http.www.advaoptical.com.ns.yang.fsp150cm.facility.rev200710.sub.network.network.element.shelf.slot.card.ethernet.card.EthernetPort;
import org.opendaylight.yang.gen.v1.http.www.advaoptical.com.ns.yang.fsp150cm.fpm.rev200710.NetworkElement1;
import org.opendaylight.yang.gen.v1.http.www.advaoptical.com.ns.yang.fsp150cm.fpm.rev200710.NetworkElement1Builder;
import org.opendaylight.yang.gen.v1.http.www.advaoptical.com.ns.yang.fsp150cm.fpm.rev200710.flowpoint.info.group.Flowpoint;
import org.opendaylight.yang.gen.v1.http.www.advaoptical.com.ns.yang.fsp150cm.fpm.rev200710.sub.network.network.element.MpFlow;
import org.opendaylight.yang.gen.v1.http.www.advaoptical.com.ns.yang.fsp150cm.fpm.rev200710.sub.network.network.element.MpFlowBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.link.NodeEdgePoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class VLANProvisioning {
    private static final Logger LOG = LoggerFactory.getLogger(VLANProvisioning.class);
    private MountPointService mps;
    private ArrayList<NodeEdgePoint> traversedNEPs;
    private String vlanID;
    private VlanTag vlanTag;
    private ArrayList<InstanceIdentifier<?>> flowpointIIDList;

    public VLANProvisioning(MountPointService mps, ArrayList<NodeEdgePoint> traversedNEPs, String vlanID) throws ProvisioningException {
        this.mps = mps;
        this.traversedNEPs = traversedNEPs;
        this.vlanID = vlanID;

        // TODO to be fixed, priority added
        String vlan = vlanID+"-7";
        try {
            vlanTag = new VlanTag(vlan);
        } catch (IllegalArgumentException ex){
            LOG.error("VLANProvisioning- VLAN Tag String does not match regex");
            throw new ProvisioningException("VLAN Tag not correctly formatted");
        }
        LOG.info("VLANProvisioning - VLAN Tag "+ vlanTag);
    }


    // this method creates a VLAN for a "flow" e.g. a MultiFlow points in Adva FSP150,
    // - setting eth-port vlan config on access interfaces
    // - declaring a FP with this VLAN over each interface (e.g. eth-port) for each node
    // - setting VLAN config on that FP
    // - creating the MultiFlow in each node, grouping FP of same VLAN
    void createVLAN() throws ProvisioningException {
        // Configure interfaces in each node, for each node 2 FPs that have to be used to create the MPFlow
        // First and last interface should be configured as access port, so in a different way
        for(int i=0; i<traversedNEPs.size(); i=i+2){
            ArrayList<InstanceIdentifier<?>> flowpointList = new ArrayList<>();
            LOG.info("VLANProvisioning - Creating flowpoints for node "+traversedNEPs.get(i).getNodeUuid().getValue() +"... ");

            AdvaEthernetPortBuilder builder1 = initAdvaEthernetPortBuilder(traversedNEPs.get(i));
            // first interface config as access port
            if( i==0 ){
                builder1.setAsAccessPort();
            }
            flowpointList.add(builder1.createFlowpoint());
            storeAdvaEthernetPortBuilder(builder1);

            AdvaEthernetPortBuilder builder2 = initAdvaEthernetPortBuilder(traversedNEPs.get(i+1));
            // last interface config as access port
            if( i+1 == traversedNEPs.size()-1){
                builder2.setAsAccessPort();
            }
            flowpointList.add(builder2.createFlowpoint());
            storeAdvaEthernetPortBuilder(builder2);
            createMultiPointFlow(traversedNEPs.get(i),traversedNEPs.get(i),flowpointList);
        }
    }


    private AdvaEthernetPortBuilder initAdvaEthernetPortBuilder(NodeEdgePoint nep) throws ProvisioningException {
        AdvaFSP150XG400Driver netconfDriver = new AdvaFSP150XG400Driver(mps);

        String nodeId = nep.getNodeUuid().getValue();
        // NEP Uuid is similar to 1-1-1-1
        String[] nepInfo = nep.getNodeEdgePointUuid().getValue().split("-");
        Long networkElement = Long.valueOf(nepInfo[0]);
        Long shelf = Long.valueOf(nepInfo[1]);
        Long slot = Long.valueOf(nepInfo[2]);
        Long portId = Long.valueOf(nepInfo[3]);
        // retrieve current ethport
        Optional<EthernetPort> optionalEthernetPort = netconfDriver.getEthernetPort(nodeId,networkElement,shelf,slot,portId);
        if(!optionalEthernetPort.isPresent()){
            throw new ProvisioningException("Ethernet Port in node "+nodeId + " and portId "+portId +" not defined.");
        }
        EthernetPort ethPort = optionalEthernetPort.get();
        AdvaEthernetPortBuilder builder = new AdvaEthernetPortBuilder(nep, ethPort,vlanTag);
        return builder;
    }

    private void storeAdvaEthernetPortBuilder(AdvaEthernetPortBuilder builder) throws ProvisioningException {
        AdvaFSP150XG400Driver netconfDriver = new AdvaFSP150XG400Driver(mps);

        netconfDriver.setEthernetPortAsAccessPort(builder.getNodeId(),builder.getNetworkElement(),builder.getShelf(),
                builder.getSlot(),builder.getPortId(),builder.getEthernetPort());
    }


    private void createMultiPointFlow(NodeEdgePoint nep1, NodeEdgePoint nep2, List<InstanceIdentifier<?>> fpList) throws ProvisioningException {
        // Both NEPs belong to the same node
        String nodeId = nep1.getNodeUuid().getValue();
        String[] nepInfo = nep1.getNodeEdgePointUuid().getValue().split("-");
        Long networkElement = Long.valueOf(nepInfo[0]);

        // Retrieve current MPF list
        List<MpFlow> flowList = null;
        Long flowId = Long.valueOf(1);

        AdvaFSP150XG400Driver netconfDriver = new AdvaFSP150XG400Driver(mps);
        Optional<NetworkElement1> optAugment = netconfDriver.getMultiPointFlows(nodeId,networkElement);
        if(optAugment.isPresent()){
            flowList = optAugment.get().getMpFlow();
            if(flowList!=null && !flowList.isEmpty()){
                flowId = Long.valueOf(flowList.size()+1);
            }
        }

        // create a new MPF
        MpFlowBuilder builder = new MpFlowBuilder()
                .setMpFlowId(flowId)
                .setFlowpoint(fpList)
                .setAdminState(AdminState.InService)
                .setTableFullAction(LearningType.Forward);

        NetworkElement1Builder networkElement1Builder;

        if(flowList==null){
            flowList = new ArrayList<>();
        }
        flowList.add(builder.build());
        networkElement1Builder = new NetworkElement1Builder()
                .setMpFlow(flowList);
        netconfDriver.createMultiPointFlow(nodeId,networkElement,networkElement1Builder.build());
        LOG.info("VLANProvisioning - Created MPFlow for node "+nodeId);
    }


    // this method deletes a VLAN for a "flow" e.g. a MultiFlow point in Adva FSP150
    // trying to delete a FP with the MPFlow referencing it causes an error.
    // first identify and remove the MPFlow, then remove each FP of that flow
    public void deleteVLAN() throws ProvisioningException {
        // First, need to remove the MPFlow, and in order to identify which is the one to be removed,
        // store all the EthBuilder and removed flowpoints
        // then remove the MPFlow
        // then update all the eth-ports and flowpoints
        ArrayList<InstanceIdentifier<?>> allFlowpoints = new ArrayList<>();
        for(int i=0; i<traversedNEPs.size(); i=i+2){
            ArrayList<InstanceIdentifier<?>> flowpointList = new ArrayList<>();
            LOG.info("VLANProvisioning - Deleting flowpoints for node "+traversedNEPs.get(i).getNodeUuid().getValue() +"... ");

            AdvaEthernetPortBuilder builder1 = initAdvaEthernetPortBuilder(traversedNEPs.get(i));
            InstanceIdentifier<?> iid1 = builder1.deleteFlowpoint();
            if( iid1 == null){
                throw new ProvisioningException("Flowpoint with VLANTAG not found");
            }
            flowpointList.add(iid1);
            // first interface config as access port
            if( i==0 ){
                builder1.resetAccessPort();
            }


            AdvaEthernetPortBuilder builder2 = initAdvaEthernetPortBuilder(traversedNEPs.get(i+1));
            InstanceIdentifier<?> iid2 = builder2.deleteFlowpoint();
            if( iid2 == null){
                throw new ProvisioningException("Flowpoint with VLANTAG not found");
            }
            flowpointList.add(iid2);
            // last interface config as access port
            if( i+1 == traversedNEPs.size()-1){
                builder2.resetAccessPort();
            }

            // First remove the MPflow for this node, then write the new eth-port
            deleteMultiPointFlow(traversedNEPs.get(i),traversedNEPs.get(i),flowpointList);
            storeAdvaEthernetPortBuilder(builder1);
            storeAdvaEthernetPortBuilder(builder2);

        }
    }

    // This method deletes a MPFlow composed of the FPs in fpList
    private void deleteMultiPointFlow(NodeEdgePoint nep1, NodeEdgePoint nep2, List<InstanceIdentifier<?>> fpList) throws ProvisioningException {
        // Both NEPs belong to the same node
        String nodeId = nep1.getNodeUuid().getValue();
        String[] nepInfo = nep1.getNodeEdgePointUuid().getValue().split("-");
        Long networkElement = Long.valueOf(nepInfo[0]);

        // Retrieve current MPF list
        List<MpFlow> flowList = null;

        AdvaFSP150XG400Driver netconfDriver = new AdvaFSP150XG400Driver(mps);
        Optional<NetworkElement1> optAugment = netconfDriver.getMultiPointFlows(nodeId,networkElement);
        if(!optAugment.isPresent()){
            throw new ProvisioningException("mp-flow augment not found for node "+nodeId +" and networkElement "+networkElement);
        }
        flowList = optAugment.get().getMpFlow();
        if(flowList==null || flowList.isEmpty()){
            throw new ProvisioningException("mp-flow list is EMPTY for node "+nodeId +" and networkElement "+networkElement);
        }
        Long flowpointID = Long.valueOf(0);
        for(MpFlow flow : flowList){
            if( compareFlowpointLists(flow.getFlowpoint(),fpList) ){
                LOG.info("MPFlow found! It will be removed..." );
                flowpointID = flow.getMpFlowId();
            }
        }
        if(flowpointID == Long.valueOf(0)){
            throw new ProvisioningException("MPFlow not found in node "+nodeId +" and networkElement "+networkElement);
        }
        // remove flowpointid from device
        if(netconfDriver.deleteMultiPointFlow(nodeId,networkElement,flowpointID)==false){
            throw new ProvisioningException("Error deleting from "+nodeId +" and networkElement "+networkElement + " MPFlow with ID "+flowpointID);
        }
    }

    // utility method to compare 2 lists of IIDs
    private boolean compareFlowpointLists(List<InstanceIdentifier<?>> list1, List<InstanceIdentifier<?>> list2){
        if(list1.size()==list2.size()){
            for(int i=0; i<list1.size(); i++){
                InstanceIdentifier<Flowpoint> iid1 = (InstanceIdentifier<Flowpoint>)list1.get(i);
//                LOG.info(""+list2.contains(iid1));
                if(!list2.contains(iid1)){
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }
}
