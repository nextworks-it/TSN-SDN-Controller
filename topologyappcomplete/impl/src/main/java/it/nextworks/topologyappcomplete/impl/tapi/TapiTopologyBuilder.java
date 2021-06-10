package it.nextworks.topologyappcomplete.impl.tapi;

import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.*;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.Link;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.Node;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.node.OwnedNodeEdgePoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.context.Topology;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.context.TopologyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class TapiTopologyBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(TapiTopologyBuilder.class);
    protected TopologyBuilder builder;
    protected Uuid topologyUuid;

    public TapiTopologyBuilder(org.opendaylight.yang.gen.v1.it.nextworks.yang.topologyappcomplete.rev200514.add._static.topology.input.Topology input){
        topologyUuid = input.getUuid();
        LOG.info("TapiTopologyBuilder: Created topology with Uuid: "+topologyUuid);

        List<Node> nodeList = input.getNode();
        List<Link> linkList = input.getLink();
        builder =  new TopologyBuilder().setUuid(topologyUuid).setLink(linkList).setNode(nodeList);
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