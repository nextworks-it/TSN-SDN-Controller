package it.nextworks.provisioningappcomplete.impl.netconf;

import org.opendaylight.mdsal.binding.api.*;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class MountpointManager {
    private static final Logger LOG = LoggerFactory.getLogger(MountpointManager.class);
    private final MountPointService mountPointService;

    public MountpointManager(MountPointService mps){
        this.mountPointService = mps;
    }

    private DataBroker getDataBroker(String nodeId){
        InstanceIdentifier<Node> nodeIID = NetconfUtils.netconfNodeIid(nodeId);
        final Optional<MountPoint> mpOptional = mountPointService.getMountPoint(nodeIID);
        if(mpOptional==null || !mpOptional.isPresent()){
            LOG.error("Mountpoint for node " + nodeId + " not found.");
            return null;
        }
        final MountPoint mountPoint = mpOptional.get();
        final DataBroker dataBrokerDevice = mountPoint.getService(DataBroker.class).get();
        return dataBrokerDevice;
    }


    public <T extends DataObject> Optional<T> readFromDataStore(InstanceIdentifier<T> iid, String nodeId) {
        final DataBroker dataBrokerDevice = getDataBroker(nodeId);
        final ReadTransaction readTrx = dataBrokerDevice.newReadOnlyTransaction();

        Optional<T> data;
        try {
            data = readTrx.read(LogicalDatastoreType.CONFIGURATION, iid).get();
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Error reading from data store for node " + nodeId);
            e.printStackTrace();
            return Optional.empty();
        } finally {
            readTrx.close();
        }
        return data;
    }

    public <T extends DataObject> boolean writeToDataStore(InstanceIdentifier<T> iid, String nodeId, T content) {
        final DataBroker dataBrokerDevice = getDataBroker(nodeId);
        final WriteTransaction writeTx = dataBrokerDevice.newWriteOnlyTransaction();

        try {
            writeTx.put(LogicalDatastoreType.CONFIGURATION, iid,content);
            writeTx.commit().get();
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Error writing data store for node " + nodeId);
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public <T extends DataObject> boolean writeMergeToDataStore(InstanceIdentifier<T> iid, String nodeId, T content) {
        final DataBroker dataBrokerDevice = getDataBroker(nodeId);
        final WriteTransaction writeTx = dataBrokerDevice.newWriteOnlyTransaction();

        try {
            writeTx.put(LogicalDatastoreType.CONFIGURATION, iid,content);
            writeTx.commit().get();
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Error writing data store for node " + nodeId);
            e.printStackTrace();
            return false;
        }
        return true;
    }


    public <T extends DataObject> boolean deleteFromDataStore(InstanceIdentifier<T> iid, String nodeId) {
        final DataBroker dataBrokerDevice = getDataBroker(nodeId);
        final WriteTransaction writeTx = dataBrokerDevice.newWriteOnlyTransaction();

        try {
            writeTx.delete(LogicalDatastoreType.CONFIGURATION, iid);
            writeTx.commit().get();
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Error writing data store for node " + nodeId);
            e.printStackTrace();
            return false;
        }
        return true;
    }
}