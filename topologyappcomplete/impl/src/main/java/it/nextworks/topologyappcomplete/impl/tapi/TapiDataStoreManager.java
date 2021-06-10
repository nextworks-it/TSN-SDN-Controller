package it.nextworks.topologyappcomplete.impl.tapi;

import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.ReadTransaction;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.Context;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.ContextBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.tapi.context.ServiceInterfacePoint;
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


    // Have to initialize the Context "node" in the ODL tree, otherwise future topology (or other) writings would fail
    public static void initTapiContext(DataBroker dataBroker){
        final ReadTransaction transaction = dataBroker.newReadOnlyTransaction();
        InstanceIdentifier<Context> iidContext = InstanceIdentifier.builder(Context.class)
                .build();
        Boolean data;
        try {
            data = transaction.exists(LogicalDatastoreType.OPERATIONAL, iidContext).get();
            if (!data) {
                LOG.info("TapiManager - initTapiContext: No Tapi context initialized. Initializing the TAPI context ...");
                final WriteTransaction wtx = dataBroker.newWriteOnlyTransaction();
                Context ctx = new ContextBuilder().build();
                wtx.put(LogicalDatastoreType.OPERATIONAL,iidContext,ctx);
                wtx.commit();
            } else {
                LOG.warn("TapiManager - initTapiContext: Tapi Context already initialized");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("TapiManager - initTapiContext: Error while initializing context from the data tree.");
            LOG.error(e.getMessage());
        } finally {
            transaction.close();
        }
    }
}
