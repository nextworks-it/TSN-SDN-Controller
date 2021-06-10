package it.nextworks.topologyappcomplete.impl.netconf;

import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.global._class.Name;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.topology.rev200423.topology.Node;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NetconfInfoUtils {
    private static final Logger LOG = LoggerFactory.getLogger(NetconfInfoUtils.class);
    private static final String managementAddress = "management-address";
    private static final String managementPort = "management-port";

    private NetconfInfoUtils() {
        throw new IllegalStateException("Instantiating utility class.");
    }

    public static String getManagementAddress(List<Name> nameList){
        for(Name name : nameList){
            if(name.getValueName().equals(managementAddress))
                return name.getValue();
        }
        return null;
    }

    public static String getManagementPort(List<Name> nameList){
        for(Name name : nameList){
            if(name.getValueName().equals(managementPort))
                return name.getValue();
        }
        return null;
    }

    // Check if every node in the TAPI topology in input has mngm-ip and mngm-port. It returns:
    // TRUE if all nodes have mngm-ip and mngm-port
    // FALSE if at least one node does not have this info
    public static boolean checkManagementInfo(List<Node> nodeList){
        for(Node node : nodeList){
            String mngAddr = NetconfInfoUtils.getManagementAddress(node.getName());
            if(mngAddr == null || mngAddr.isEmpty()) {
                LOG.error("addStaticTopology - Missing management-address for node "+node.getUuid().getValue());
                return false;
            }
            String mngPort = NetconfInfoUtils.getManagementPort(node.getName());
            if(mngPort == null || mngPort.isEmpty()){
                LOG.error("addStaticTopology - Missing management-port for node "+node.getUuid().getValue());
                return false;
            }
        }
        return true;
    }
}
