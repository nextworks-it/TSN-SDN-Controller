package it.nextworks.provisioningappcomplete.impl.provisioning;

import it.nextworks.provisioningappcomplete.impl.provisioning.exceptions.PathComputationException;
import it.nextworks.provisioningappcomplete.impl.provisioning.exceptions.ProvisioningException;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.path.computation.rev200423.path.computation.context.Path;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.link.NodeEdgePoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.link.NodeEdgePointBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.node.OwnedNodeEdgePoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.node.edge.point.MappedServiceInterfacePoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.Link;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.Node;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.context.Topology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PathComputationUtils {
    private static final Logger LOG = LoggerFactory.getLogger(PathComputationUtils.class);
    private PathComputationUtils() {
        throw new IllegalStateException("Instantiating utility class.");
    }

    public static ArrayList<NodeEdgePoint> buildTraversedNEPList(Topology topology, Path path, ArrayList<String> sipList)
            throws ProvisioningException, PathComputationException {
        // Retrive all the (TAPI Topology) links traversed in the computed path
        ArrayList<Link> traversedTopologyLinks = retriveTraversedTopologyLink(topology,path);
        // Retrieve internal traversed NEPs (excluding the ones with the SIP, added later)
        ArrayList<NodeEdgePoint> traversedNEPs = retrieveInternalTraversedNEPS(traversedTopologyLinks);
        // Adding edge NEPs (related to the SIPs)
        NodeEdgePoint firstNEP = retrieveNEP(topology, sipList.get(0));
        NodeEdgePoint lastNEP = retrieveNEP(topology, sipList.get(1));
        if(firstNEP == null || lastNEP == null){
            throw new ProvisioningException("Empty NodeEdgePoint from SIPs");
        }
        traversedNEPs.add(0,firstNEP);
        traversedNEPs.add(lastNEP);
        LOG.info("ALL traversed NEPs "+printTraversedNEPs(traversedNEPs));
        return traversedNEPs;
    }

    private static String printTraversedNEPs(ArrayList<NodeEdgePoint> neps){
        String output = "\n";
        for(NodeEdgePoint nep: neps){
            output = output + "{ "+nep.getNodeUuid().getValue()+" - "+nep.getNodeEdgePointUuid().getValue()+" }\n";
        }
        return output;
    }

    private static ArrayList<Link> retriveTraversedTopologyLink(Topology topology, Path path) throws PathComputationException {
        List<Link> topologyLinks = topology.getLink();
        ArrayList<Link> traversedTopologyLinks = new ArrayList<>();
        List<org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.path.computation.rev200423.path.Link> pathLinks = path.getLink();
        if(pathLinks != null) {
            // For each link in the computed path -> search the corresponding topology link
            for (org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.path.computation.rev200423.path.Link pathLink : pathLinks) {
//                LOG.info("retriveTopologyLink - ERO " + pathLink.getLinkUuid().getValue());
                Iterator iterator = topologyLinks.iterator();
                while (iterator.hasNext()) {
                    Link topologyLink = (Link) iterator.next();
//                    LOG.info("retriveTopologyLink - topologyLink" + topologyLink.getUuid().getValue());
                    if (topologyLink.getUuid().getValue().equals(pathLink.getLinkUuid().getValue())) {
                        traversedTopologyLinks.add(topologyLink);
                        LOG.info("retriveTopologyLink - traversedTopologyLink " + topologyLink.getUuid().getValue());
                        break;
                    }
                }
            }
            return traversedTopologyLinks;
        } else {
            LOG.error("retriveTopologyLink - Computed Path with empty ERO");
            throw new PathComputationException("Computed Path with empty ERO");
        }
    }

    private static NodeEdgePoint retrieveNEP(Topology topology, String sipUuid){
        List<Node> topologyNodes = topology.getNode();
        for(Node node: topologyNodes){
            for(OwnedNodeEdgePoint onep: node.getOwnedNodeEdgePoint()){
                if(onep.getMappedServiceInterfacePoint() != null) {
                    for (MappedServiceInterfacePoint msip : onep.getMappedServiceInterfacePoint()) {
                        if (sipUuid.equals(msip.getServiceInterfacePointUuid().getValue())) {
                            NodeEdgePointBuilder nodeEdgePointBuilder = new NodeEdgePointBuilder()
                                    .setTopologyUuid(topology.getUuid())
                                    .setNodeUuid(node.getUuid())
                                    .setNodeEdgePointUuid(onep.getUuid());
                            return nodeEdgePointBuilder.build();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static ArrayList<NodeEdgePoint> retrieveInternalTraversedNEPS(ArrayList<Link> traversedTopologyLinks){
        ArrayList<NodeEdgePoint> traversedNEPs = new ArrayList<>();
        for(Link link: traversedTopologyLinks){
            // Add 2 termination point, only internal NEP
            traversedNEPs.add(link.getNodeEdgePoint().get(0));
            traversedNEPs.add(link.getNodeEdgePoint().get(1));
        }
        return traversedNEPs;
    }
}
