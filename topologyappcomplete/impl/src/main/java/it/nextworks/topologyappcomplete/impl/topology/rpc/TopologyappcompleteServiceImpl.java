package it.nextworks.topologyappcomplete.impl.topology.rpc;

import com.google.common.util.concurrent.ListenableFuture;
import it.nextworks.topologyappcomplete.impl.topology.exceptions.TopologyCreationException;
import it.nextworks.topologyappcomplete.impl.topology.exceptions.TopologyRemovalException;
import it.nextworks.topologyappcomplete.impl.topology.TopologyService;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.yang.gen.v1.it.nextworks.yang.topologyappcomplete.rev200514.*;
import org.opendaylight.yangtools.yang.binding.RpcOutput;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TopologyappcompleteServiceImpl implements TopologyappcompleteService {
    private static final Logger LOG = LoggerFactory.getLogger(TopologyappcompleteServiceImpl.class);
    private DataBroker dataBroker;
    private String netconfUsername;
    private String netconfPsw;

    public TopologyappcompleteServiceImpl(DataBroker db, String netconfUsername, String netconfPsw){
        this.dataBroker = db;
        this.netconfUsername = netconfUsername;
        this.netconfPsw = netconfPsw;
    }

    // Receive in input a TAPI topology definition and
    // if the topology UUID does not exist yet
    // create netconf-connector for each node
    // create a SIP for each mapped-sip on the nodes
    // store them
    @Override
    public ListenableFuture<RpcResult<AddStaticTopologyOutput>> addStaticTopology(AddStaticTopologyInput input) {
        LOG.info("TopologyappcompleteService - addStaticTopology");
        TopologyService topologyService = new TopologyService(dataBroker,netconfUsername,netconfPsw);
        try {
            topologyService.createStaticTopology(input);
        } catch (TopologyCreationException ex){
            return buildGenericErrorMessage(AddStaticTopologyOutput.class, ex.getMessage());
        }
        RpcResultBuilder<AddStaticTopologyOutput> rpcResultBuilder = RpcResultBuilder.success();
        return rpcResultBuilder.buildFuture();
    }

    // Remove the TAPI topology and all the netconf-connector of the topology
    @Override
    public ListenableFuture<RpcResult<RemoveStaticTopologyOutput>> removeStaticTopology(RemoveStaticTopologyInput input) {
        LOG.info("TopologyappcompleteService - removeStaticTopology");
        TopologyService topologyService = new TopologyService(dataBroker,netconfUsername,netconfPsw);
        try {
            topologyService.deleteStaticTopology(input.getTopology());
        } catch (TopologyRemovalException ex){
            return buildGenericErrorMessage(RemoveStaticTopologyOutput.class, ex.getMessage());
        }
        RpcResultBuilder<RemoveStaticTopologyOutput> rpcResultBuilder = RpcResultBuilder.success();
        return rpcResultBuilder.buildFuture();
    }

    // Method to handle errors
    private <T extends RpcOutput> ListenableFuture<RpcResult<T>> buildGenericErrorMessage(Class<T> name, String errorMessage){
        RpcResultBuilder<T> rpcResultBuilder = RpcResultBuilder.failed();
        rpcResultBuilder.withError(RpcError.ErrorType.APPLICATION, errorMessage);
        LOG.error(errorMessage);
        return rpcResultBuilder.buildFuture();
    }
}
