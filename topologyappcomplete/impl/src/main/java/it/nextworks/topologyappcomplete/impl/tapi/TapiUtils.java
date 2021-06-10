package it.nextworks.topologyappcomplete.impl.tapi;

import  org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.Context;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.Uuid;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.tapi.context.ServiceInterfacePoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.tapi.context.ServiceInterfacePointKey;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.Context1;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.context.TopologyContext;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.node.OwnedNodeEdgePoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.node.OwnedNodeEdgePointKey;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.Link;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.LinkKey;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.Node;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.context.Topology;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.context.TopologyKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TapiUtils {
    private static final Logger LOG = LoggerFactory.getLogger(TapiUtils.class);
    private TapiUtils() {
        throw new IllegalStateException("Instantiating utility class.");
    }

    public static InstanceIdentifier<Context> contextIid() {
        return InstanceIdentifier.builder(Context.class).build();
    }

    public static InstanceIdentifier<ServiceInterfacePoint> serviceInterfacePointIId() {
        return InstanceIdentifier.create(Context.class)
                .child(ServiceInterfacePoint.class);
    }

    public static InstanceIdentifier<ServiceInterfacePoint> serviceInterfacePointIId(String name) {
        return InstanceIdentifier.create(Context.class)
                .child(ServiceInterfacePoint.class, new ServiceInterfacePointKey(new Uuid(name)));

    }

    public static InstanceIdentifier<TopologyContext> topologies() {
        return InstanceIdentifier.create(Context.class)
                .augmentation(Context1.class)
                .child(TopologyContext.class);
    }

    public static InstanceIdentifier<TopologyContext> topologyContextIId() {
        return InstanceIdentifier.create(Context.class)
                .augmentation(Context1.class)
                .child(TopologyContext.class);
    }

    public static InstanceIdentifier<Topology> topologyIId(final String topologyId) {
        return InstanceIdentifier.create(Context.class)
                .augmentation(Context1.class)
                .child(TopologyContext.class)
                .child(Topology.class, new TopologyKey(new Uuid(topologyId)));
    }

    public static InstanceIdentifier<Node> nodeIId(final String topologyId, final String nodeId) {
        return InstanceIdentifier.create(Context.class)
                .augmentation(Context1.class)
                .child(TopologyContext.class)
                .child(Topology.class, new TopologyKey(new Uuid(topologyId)))
                .child(Node.class, new NodeKey(new Uuid(nodeId)));
    }

    public static InstanceIdentifier<Link> linkIId(final String topologyId, final String linkId) {
        return InstanceIdentifier.create(Context.class)
                .augmentation(Context1.class)
                .child(TopologyContext.class)
                .child(Topology.class, new TopologyKey(new Uuid(topologyId)))
                .child(Link.class, new LinkKey(new Uuid(linkId)));
    }

    public static InstanceIdentifier<OwnedNodeEdgePoint> nepIid(final String topologyId, final String nodeId, final String nepId) {
        return InstanceIdentifier.create(Context.class)
                .augmentation(Context1.class)
                .child(TopologyContext.class)
                .child(Topology.class, new TopologyKey(new Uuid(topologyId)))
                .child(Node.class, new NodeKey(new Uuid(nodeId)))
                .child(OwnedNodeEdgePoint.class, new OwnedNodeEdgePointKey(new Uuid(nepId)));
    }

}