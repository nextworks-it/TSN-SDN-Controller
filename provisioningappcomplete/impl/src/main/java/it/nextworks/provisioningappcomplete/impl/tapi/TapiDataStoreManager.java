package it.nextworks.provisioningappcomplete.impl.tapi;

import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.Context;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.ContextBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.tapi.context.ServiceInterfacePoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.context.Connection;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.context.ConnectivityService;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.context.ConnectivityContext;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.context.ConnectivityContextBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.Context1;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.Context1Builder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.context.TopologyContext;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.context.TopologyContextBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.context.Topology;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class TapiDataStoreManager {
    private static final Logger LOG = LoggerFactory.getLogger(TapiDataStoreManager.class);

    private static <T extends DataObject> boolean existsInDataStore(DataBroker dataBroker, LogicalDatastoreType datastoreType, InstanceIdentifier<T> iid){
        final ReadTransaction transaction = dataBroker.newReadOnlyTransaction();
        Boolean data;
        try {
            data = transaction.exists(datastoreType, iid).get();
            if(!data){
                LOG.warn("TapiManager - "+ iid + " DOES NOT exists in "+datastoreType+" DS");
                return false;
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error(e.getMessage());
            return false;
        } finally {
            transaction.close();
        }
        return true;
    }

    private static <T extends DataObject> boolean writeInDataStore(DataBroker dataBroker, LogicalDatastoreType datastoreType,
                                                                   InstanceIdentifier<T> iid, T data){
        WriteTransaction wtx = dataBroker.newWriteOnlyTransaction();
        LOG.info("TapiManager - writing "+ data.implementedInterface().getSimpleName() +" in "+datastoreType+" DS");
        wtx.put(datastoreType, iid, data);
        try {
            wtx.commit().get();
            LOG.info("TapiManager - "+ data.implementedInterface().getSimpleName() +" written in "+datastoreType+" DS");
            return true;
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static <T extends DataObject> boolean deleteFromDataStore(DataBroker dataBroker, LogicalDatastoreType datastoreType,InstanceIdentifier<T> iid){
        WriteTransaction wtx = dataBroker.newWriteOnlyTransaction();
        wtx.delete(datastoreType, iid);
        try {
            wtx.commit().get();
            return true;
        } catch (final InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return false;
        }
    }

    private static <T extends DataObject> T readFromDataStore(DataBroker dataBroker, LogicalDatastoreType datastoreType, InstanceIdentifier<T> iid){
        if(existsInDataStore(dataBroker,datastoreType,iid)){
            final ReadTransaction transaction = dataBroker.newReadOnlyTransaction();
            Optional<T> opt;
            try {
                opt = transaction.read(datastoreType, iid).get();
                return opt.get();
            } catch (InterruptedException | ExecutionException e) {
                LOG.error(e.getMessage());
                return null;
            } finally {
                transaction.close();
            }
        } else
            return null;
    }

    // TOPOLOGY
    public static boolean existTopology(DataBroker dataBroker, String topologyId) {
        return existsInDataStore(dataBroker, LogicalDatastoreType.OPERATIONAL,TapiUtils.topologyIId(topologyId));
    }

    public static Topology readTopology(DataBroker dataBroker, String topologyId){
        Topology tapiTopology = TapiDataStoreManager.readFromDataStore(dataBroker, LogicalDatastoreType.OPERATIONAL, TapiUtils.topologyIId(topologyId));
        return tapiTopology;
    }

    public static Topology readTopology(DataBroker dataBroker){
        TopologyContext topologyContext = TapiDataStoreManager.readFromDataStore(dataBroker, LogicalDatastoreType.OPERATIONAL, TapiUtils.topologyContextIId());
        return topologyContext.getTopology().get(0);
    }

    public static boolean writeTopology(DataBroker dataBroker, Topology topology){
        InstanceIdentifier<TopologyContext> iidtc = TapiUtils.topologyContextIId();
        ArrayList<Topology> topologies = new ArrayList<>();
        topologies.add(topology);
        ContextBuilder cxt = new ContextBuilder();
        TopologyContext topologyContext = new TopologyContextBuilder()
                .setTopology(topologies)
                .build();
        Context1Builder topoaugm = new Context1Builder()
                .setTopologyContext(topologyContext);
        cxt.addAugmentation(Context1.class, topoaugm.build());
        return writeInDataStore(dataBroker,LogicalDatastoreType.OPERATIONAL, iidtc, topologyContext);
    }

    public static boolean deleteTopology(DataBroker dataBroker, String topologyUuid){
        InstanceIdentifier<Topology> iidtc = TapiUtils.topologyIId(topologyUuid);
        return deleteFromDataStore(dataBroker,LogicalDatastoreType.OPERATIONAL,iidtc);
    }

    // SIP
    public static boolean writeSIPList(DataBroker dataBroker, List<ServiceInterfacePoint> sipList){
        Context currentContext = readFromDataStore(dataBroker,LogicalDatastoreType.OPERATIONAL,TapiUtils.contextIid());
        InstanceIdentifier<Context> iid = TapiUtils.contextIid();
        LOG.info("TapiManager - writeSIPList - Writing SIP lists in OPERATIONAL DS");
        ContextBuilder cxt = new ContextBuilder(currentContext);
        cxt.setServiceInterfacePoint(sipList);
        return writeInDataStore(dataBroker,LogicalDatastoreType.OPERATIONAL,iid, cxt.build());

    }

    public static List<ServiceInterfacePoint> readSIPList(DataBroker dataBroker){
        Context ctx = TapiDataStoreManager.readFromDataStore(dataBroker, LogicalDatastoreType.OPERATIONAL, TapiUtils.contextIid());
        return ctx.getServiceInterfacePoint();
    }

    // CONNECTIVITY-CONTEXT
    public static ConnectivityContext readConnectivityContext(DataBroker dataBroker){
        ConnectivityContext cCtx = TapiDataStoreManager.readFromDataStore(dataBroker, LogicalDatastoreType.OPERATIONAL, TapiUtils.connectivityContextIid());
        return cCtx;
    }

    public static boolean writeConnectivityContext(DataBroker dataBroker, ConnectivityContext connectivityContext){
        return writeInDataStore(dataBroker,LogicalDatastoreType.OPERATIONAL,TapiUtils.connectivityContextIid(),connectivityContext);
    }

    // CONNECTIVITY-SERVICE
    public static Boolean existConnectivityService(DataBroker dataBroker, String csUuid){
        return TapiDataStoreManager.existsInDataStore(dataBroker, LogicalDatastoreType.OPERATIONAL, TapiUtils.connectivityServiceIid(csUuid));
    }

    public static ConnectivityService readConnectivityService(DataBroker dataBroker, String csUuid){
        ConnectivityService cs = TapiDataStoreManager.readFromDataStore(dataBroker, LogicalDatastoreType.OPERATIONAL, TapiUtils.connectivityServiceIid(csUuid));
        return cs;
    }

    public static boolean deleteConnectivityService(DataBroker dataBroker, String csUuid){
        InstanceIdentifier<ConnectivityService> iid = TapiUtils.connectivityServiceIid(csUuid);
        return deleteFromDataStore(dataBroker,LogicalDatastoreType.OPERATIONAL,iid);
    }

    // CONNECTIONS
    public static List<Connection> readConnections(DataBroker dataBroker){
        ConnectivityContext ctx = TapiDataStoreManager.readFromDataStore(dataBroker, LogicalDatastoreType.OPERATIONAL, TapiUtils.connectivityContextIid());
        return ctx.getConnection();
    }

    public static boolean writeConnections(DataBroker dataBroker, List<Connection> connections){
        ConnectivityContext conCtx = readConnectivityContext(dataBroker);
        ConnectivityContextBuilder builder;
        if(conCtx!=null)
            builder = new ConnectivityContextBuilder(conCtx);
        else
            builder = new ConnectivityContextBuilder();
        builder.setConnection(connections);
        return writeConnectivityContext(dataBroker,builder.build());
    }

}
