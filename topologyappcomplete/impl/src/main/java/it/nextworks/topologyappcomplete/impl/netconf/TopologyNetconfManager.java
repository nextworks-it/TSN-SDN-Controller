package it.nextworks.topologyappcomplete.impl.netconf;

import org.opendaylight.mdsal.binding.api.*;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Host;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.NetconfNodeBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netconf.node.topology.rev150114.netconf.node.credentials.credentials.LoginPasswordBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutionException;

public class TopologyNetconfManager {
    private static final Logger LOG = LoggerFactory.getLogger(TopologyNetconfManager.class);

    private static Node prepareNode(String nodeUuidString, String ip, String port, String username, String psw){
        final NodeId nodeUuid = new NodeId(nodeUuidString);
        // Prepare NetconfNode with info for the netconf-connection
        NetconfNode netconfNode = new NetconfNodeBuilder()
                .setHost(new Host(new IpAddress(new Ipv4Address(ip))))
                .setPort(new PortNumber(Integer.valueOf(port)))
                .setSchemaless(false)
                .setCredentials(new LoginPasswordBuilder()
                        .setUsername(username).setPassword(psw).build())
                .build();
        // Prepare network-topology node
        final Node node = new NodeBuilder()
                .withKey(new NodeKey(nodeUuid))
                .setNodeId(nodeUuid).addAugmentation(NetconfNode.class,netconfNode).build();
        return node;
    }

    public static boolean createNetconfConnector(DataBroker dataBroker, String nodeUuid, String mngIp, String mngPort, String username, String psw){
        Node node = prepareNode(nodeUuid,mngIp,mngPort,username,psw);
        final WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
        transaction.put(LogicalDatastoreType.CONFIGURATION, NetconfUtils.netconfNodeIid(nodeUuid), node);
        try {
            transaction.commit().get();
            return true;
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static boolean deleteNetconfConnector(DataBroker dataBroker, String deviceName){
        if (checkIfNetconfConnectorExists(dataBroker, deviceName)){
            final WriteTransaction transaction = dataBroker.newWriteOnlyTransaction();
            transaction.delete(LogicalDatastoreType.CONFIGURATION, NetconfUtils.netconfNodeIid(deviceName));
            try {
                transaction.commit().get();
                LOG.info("TopologyNetconfManager - netconf-connector of " + deviceName + " deleted");
                return true;
            } catch (final InterruptedException | ExecutionException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            LOG.error("TopologyNetconfManager - deleteNetconfConnector: connector of " + deviceName + " does not exists");
        }
        return true;
    }

    public static boolean deleteAllNetconfConnector(DataBroker dataBroker) {
        // retrieving all netconf connectors
        final ReadTransaction readTrx = dataBroker.newReadOnlyTransaction();
        Topology data;
        try {
            data = readTrx.read(LogicalDatastoreType.CONFIGURATION, NetconfUtils.NETCONF_TOPOLOGY_IID).get().get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return false;
        }
        readTrx.close();

        // check if some netconf-connector exists
        if (data.getNode() != null) {
            for (Node node : data.getNode()) {
                deleteNetconfConnector(dataBroker,node.getNodeId().getValue());
            }
            LOG.info("TopologyNetconfManager - all netconf-connectors deleted");
        } else {
            LOG.warn("TopologyNetconfManager - no netconf-connectors present");
        }
        return true;
    }

    public static boolean checkIfNetconfConnectorExists(DataBroker dataBroker, String deviceName) {
        final ReadTransaction transaction = dataBroker.newReadOnlyTransaction();
        Boolean output = false;
        try {
            output = transaction.exists(LogicalDatastoreType.CONFIGURATION, NetconfUtils.netconfNodeIid(deviceName)).get();
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        transaction.close();
        return output;
    }
}
