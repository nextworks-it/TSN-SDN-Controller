package it.nextworks.provisioningappcomplete.impl.provisioning.rpc;

import com.google.common.util.concurrent.ListenableFuture;
import it.nextworks.provisioningappcomplete.impl.provisioning.ProvisioningService;
import it.nextworks.provisioningappcomplete.impl.provisioning.exceptions.PathComputationException;
import it.nextworks.provisioningappcomplete.impl.provisioning.exceptions.ProvisioningException;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.*;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.service.EndPoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.service.EndPointBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.create.connectivity.service.output.ServiceBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.eth.rev200423.*;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.eth.rev200423.context.connectivity.context.connectivity.service.end.point.EthConnectivityServiceEndPointSpecBuilder;
import org.opendaylight.yangtools.yang.binding.RpcOutput;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TapiConnectivityServiceImpl implements TapiConnectivityService {
    private static final Logger LOG = LoggerFactory.getLogger(TapiConnectivityServiceImpl.class);
    private DataBroker dataBroker;
    private String pceIp;
    private String pcePort;
    private MountPointService mps;

    public TapiConnectivityServiceImpl(DataBroker db, MountPointService mps, String pceIp, String pcePort){
        this.dataBroker = db;
        this.pceIp = pceIp;
        this.pcePort = pcePort;
        this.mps = mps;
    }


    @Override
    public ListenableFuture<RpcResult<UpdateConnectivityServiceOutput>> updateConnectivityService(UpdateConnectivityServiceInput input) {
        return null;
    }

    @Override
    public ListenableFuture<RpcResult<GetConnectionDetailsOutput>> getConnectionDetails(GetConnectionDetailsInput input) {
        return null;
    }

    @Override
    public ListenableFuture<RpcResult<GetConnectivityServiceDetailsOutput>> getConnectivityServiceDetails(GetConnectivityServiceDetailsInput input) {
        return null;
    }

    @Override
    public ListenableFuture<RpcResult<DeleteConnectivityServiceOutput>> deleteConnectivityService(DeleteConnectivityServiceInput input) {
        LOG.info("TapiConnectivityService - Connectivity-service delete request");
        ProvisioningService provisioningService = new ProvisioningService(dataBroker,mps,pceIp,pcePort);
        try {
            provisioningService.deleteConnectivityService(input.getServiceIdOrName());
        } catch (ProvisioningException | PathComputationException ex){
            return buildGenericErrorMessage(DeleteConnectivityServiceOutput.class, ex.getMessage());
        }
        RpcResultBuilder<DeleteConnectivityServiceOutput> rpcResultBuilder = RpcResultBuilder.success();
        return rpcResultBuilder.buildFuture();
    }

    @Override
    public ListenableFuture<RpcResult<GetConnectivityServiceListOutput>> getConnectivityServiceList(GetConnectivityServiceListInput input) {
        return null;
    }

    @Override
    public ListenableFuture<RpcResult<GetConnectionEndPointDetailsOutput>> getConnectionEndPointDetails(GetConnectionEndPointDetailsInput input) {
        return null;
    }

    @Override
    public ListenableFuture<RpcResult<CreateConnectivityServiceOutput>> createConnectivityService(CreateConnectivityServiceInput input) {
        CreateConnectivityServiceOutputBuilder builder = new CreateConnectivityServiceOutputBuilder();
        LOG.info("TapiConnectivityService - Connectivity-service creation request");
        ProvisioningService provisioningService = new ProvisioningService(dataBroker,mps,pceIp,pcePort);
        ConnectivityService cs = null;
        try {
            cs = provisioningService.createConnectivityService(input);
        } catch (ProvisioningException | PathComputationException ex){
            return buildGenericErrorMessage(CreateConnectivityServiceOutput.class, ex.getMessage());
        }
        // Transf. of input augmentation in an output augm.
        List<EndPoint> endPointList = new ArrayList<>();
        org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.create.connectivity.service.input.EndPoint inputEp1 = input.getEndPoint().get(0);
        org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.create.connectivity.service.input.EndPoint inputEp2 = input.getEndPoint().get(1);

        // Inizialize new Augmentation with VLAN info, only one augm because it is equal for both endpoints
        EndPoint2 augmInputEp1 = inputEp1.augmentation(EndPoint2.class);
        EthConnectivityServiceEndPointSpecBuilder ethConnSpec1 = new EthConnectivityServiceEndPointSpecBuilder()
                .setEthTerminationCommonPac(augmInputEp1.getEthConnectivityServiceEndPointSpec().getEthTerminationCommonPac());

        EndPointBuilder epBuilder1 = new EndPointBuilder(inputEp1).removeAugmentation(EndPoint1.class);
        EndPointBuilder epBuilder2 = new EndPointBuilder(inputEp2).removeAugmentation(EndPoint1.class);
        endPointList.add(epBuilder1.build());
        endPointList.add(epBuilder2.build());

        ServiceBuilder service = new ServiceBuilder().setUuid(cs.getUuid())
                .setEndPoint(endPointList)
                .setConnection(cs.getConnection())
                .setAdministrativeState(cs.getAdministrativeState())
                .setOperationalState(cs.getOperationalState());
        builder.setService(service.build());
        return RpcResultBuilder.success(builder.build()).buildFuture();
    }

    // Method to handle errors
    private <T extends RpcOutput> ListenableFuture<RpcResult<T>> buildGenericErrorMessage(Class<T> name, String errorMessage){
        RpcResultBuilder<T> rpcResultBuilder = RpcResultBuilder.failed();
        rpcResultBuilder.withError(RpcError.ErrorType.APPLICATION, errorMessage);
        LOG.error(errorMessage);
        return rpcResultBuilder.buildFuture();
    }
}
