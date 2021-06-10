package it.nextworks.provisioningappcomplete.impl.tapi;

import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.*;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.OwnedNodeEdgePoint1;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.OwnedNodeEdgePoint1Builder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.cep.list.ConnectionEndPoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.cep.list.ConnectionEndPointBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.context.topology.context.topology.node.owned.node.edge.point.CepList;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.context.topology.context.topology.node.owned.node.edge.point.CepListBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.link.NodeEdgePoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.node.OwnedNodeEdgePointBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.Link;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.Node;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.node.OwnedNodeEdgePoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.context.Topology;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.context.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class TapiTopologyBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(TapiTopologyBuilder.class);
    protected TopologyBuilder builder;
    protected Uuid topologyUuid;

    public TapiTopologyBuilder(Topology input){
        topologyUuid = input.getUuid();
        builder =  new TopologyBuilder(input);
        LOG.info("TapiTopologyBuilder: Created topology from old one: "+topologyUuid);
    }

    public ArrayList<org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connection.ConnectionEndPoint>
        addCEPsToTopologyNodes(ArrayList<NodeEdgePoint> traversedNEPs){
        List<Node> nodeList = builder.getNode();
        ArrayList<org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connection.ConnectionEndPoint> connCepList = new ArrayList<>();
        for(NodeEdgePoint nep: traversedNEPs){
            String CEPUuidString = UUID.randomUUID().toString();
            Uuid CEPUuid = new Uuid(CEPUuidString);
//            cepUuidList.add(CEPUuid);

            String nodeUuid = nep.getNodeUuid().getValue();
            String onepUuid = nep.getNodeEdgePointUuid().getValue();

            // Build output elem
            connCepList.add(new org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connection.ConnectionEndPointBuilder()
                    .setConnectionEndPointUuid(CEPUuid)
                    .setNodeEdgePointUuid(new Uuid(onepUuid))
                    .setNodeUuid(new Uuid(nodeUuid))
                    .setTopologyUuid(topologyUuid)
                    .build());

            Node node = retrieveNode(nodeUuid);
            List<OwnedNodeEdgePoint> onepList = node.getOwnedNodeEdgePoint();
            OwnedNodeEdgePoint onep = retrieveONEP(onepList,onepUuid);
            // remove old onep
            onepList.remove(onep);
            OwnedNodeEdgePoint1 currentCepListAugm = onep.augmentation(OwnedNodeEdgePoint1.class);
            CepListBuilder cepListObjectBuilder = null;
            if(currentCepListAugm != null){
//                LOG.info("TapiTopologyBuilder - addCEPsToTopologyNodes: Old onep has CEPs");
                // a node could already have ceps
                CepList currentCepListObject = currentCepListAugm.getCepList();
                List<ConnectionEndPoint> cepList = currentCepListObject.getConnectionEndPoint();
                cepList.add(new ConnectionEndPointBuilder().setUuid(CEPUuid).build());
                cepListObjectBuilder = new CepListBuilder(currentCepListObject).setConnectionEndPoint(cepList);
            } else {
//                LOG.info("TapiTopologyBuilder - addCEPsToTopologyNodes: Old onep does not have CEPs");
                ArrayList<ConnectionEndPoint> cepList = new ArrayList<>();
                cepList.add(new ConnectionEndPointBuilder().setUuid(CEPUuid).build());
                cepListObjectBuilder = new CepListBuilder()
                        .setConnectionEndPoint(cepList);
            }
            OwnedNodeEdgePoint1Builder cepListAugmBuilder = new OwnedNodeEdgePoint1Builder()
                    .setCepList(cepListObjectBuilder.build());
            OwnedNodeEdgePointBuilder onepBuild = new OwnedNodeEdgePointBuilder(onep)
                    .addAugmentation(OwnedNodeEdgePoint1.class,cepListAugmBuilder.build());
            LOG.info("NEW OWNEP "+onepBuild.build());
            onepList.add(onepBuild.build());
            NodeBuilder nodeBuilder = new NodeBuilder(node).setOwnedNodeEdgePoint(onepList);
            nodeList.remove(node);
            nodeList.add(nodeBuilder.build());
        }
        builder.setNode(nodeList);
        return connCepList;
    }

    private Node retrieveNode(String nodeUuid){
        for(Node node: builder.getNode()){
            if(node.getUuid().getValue().equals(nodeUuid)){
                return node;
            }
        }
        return null;
    }

    private OwnedNodeEdgePoint retrieveONEP(List<OwnedNodeEdgePoint> onepList, String onepUuid){
        for(OwnedNodeEdgePoint onep: onepList){
            if(onep.getUuid().getValue().equals(onepUuid)){
                return onep;
            }
        }
        return null;
    }

    public void removeCEPsToTopologyNodes(List<org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.route.ConnectionEndPoint> cepList){
        List<Node> nodeList = builder.getNode();
        for(org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.route.ConnectionEndPoint cep: cepList){
            outloop:
            for(Node node: nodeList){
                if(node.getUuid().equals(cep.getNodeUuid())){
                    List<OwnedNodeEdgePoint> onepList = node.getOwnedNodeEdgePoint();
                    Iterator onepsIterator = onepList.iterator();
                    while(onepsIterator.hasNext()){
                        OwnedNodeEdgePoint onep = (OwnedNodeEdgePoint)onepsIterator.next();
                        if(onep.getUuid().equals(cep.getNodeEdgePointUuid())){
                            OwnedNodeEdgePoint1 currentCepListAugm = onep.augmentation(OwnedNodeEdgePoint1.class);
                            if(currentCepListAugm != null) {
                                CepList currentCepListObject = currentCepListAugm.getCepList();
                                List<ConnectionEndPoint> cepListOnOnep = currentCepListObject.getConnectionEndPoint();
                                Iterator cepsIterator = cepListOnOnep.iterator();
                                while (cepsIterator.hasNext()) {
                                    ConnectionEndPoint cepOnOnep = (ConnectionEndPoint) cepsIterator.next();
                                    if (cepOnOnep.getUuid().equals(cep.getConnectionEndPointUuid())) {
                                        LOG.info("CEP " + cepOnOnep.getUuid().getValue() + " found, will be removed ");
                                        cepsIterator.remove();
                                        onepsIterator.remove();
                                        // //Remove current ownep, adding a new onep with the new CEP list
                                        OwnedNodeEdgePoint1Builder cepListAugmBuilder = new OwnedNodeEdgePoint1Builder()
                                                .setCepList(new CepListBuilder()
                                                        .setConnectionEndPoint(cepListOnOnep).build());
                                        OwnedNodeEdgePointBuilder onepBuild = new OwnedNodeEdgePointBuilder(onep)
                                                .addAugmentation(OwnedNodeEdgePoint1.class, cepListAugmBuilder.build());
                                        LOG.info("NEW OWNEP " + onepBuild.build());
                                        onepList.add(onepBuild.build());
                                        break outloop;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        builder.setNode(nodeList);
    }

    public Topology getTopology() {
        return builder.build();
    }

    public String toString() {
        String output =  "TapiTopology{  \n";
        for (Node n : builder.getNode()){
            output = output + toString(n);
        }
        return output + " }";
    }

    private String toString(Node node){
        String output = "";
        output = output + " UUID: "+node.getUuid().getValue() + " \n";
        for (OwnedNodeEdgePoint onep : node.getOwnedNodeEdgePoint()){
            output = output + "  OWNEP: " + onep.getUuid().getValue() + "\n";
        }
        return output;
    }
}