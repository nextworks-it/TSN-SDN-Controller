package it.nextworks.topologyappcomplete.impl.topology;

import it.nextworks.topologyappcomplete.impl.topology.rpc.TopologyappcompleteServiceImpl;
import it.nextworks.topologyappcomplete.impl.tapi.TapiDataStoreManager;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.MountPointService;
import org.opendaylight.mdsal.binding.api.RpcProviderService;
import org.opendaylight.yang.gen.v1.it.nextworks.yang.topologyappcomplete.rev200514.TopologyappcompleteService;
import org.opendaylight.yangtools.concepts.ObjectRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class TopologyappcompleteProvider {
    private static final Logger LOG = LoggerFactory.getLogger(TopologyappcompleteProvider.class);
    private final DataBroker dataBroker;
    private final RpcProviderService rpcProviderService;
    private final MountPointService mountPointService;
    private ObjectRegistration<TopologyappcompleteServiceImpl> topologyAppInterface;
    private String netconfUsername;
    private String netconfPsw;
    private String pceIp;
    private String pcePort;

    public TopologyappcompleteProvider(final DataBroker dataBroker,
                                       final RpcProviderService rpcProviderService,
                                       final MountPointService mountPointService) {
        this.dataBroker = dataBroker;
        this.rpcProviderService = rpcProviderService;
        this.mountPointService = mountPointService;
    }

    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        if(!parseTsnPropertiesFile())
            return;

        LOG.info("TopologyappcompleteProvider Session Initiated");
        TapiDataStoreManager.initTapiContext(dataBroker);
        topologyAppInterface = rpcProviderService.registerRpcImplementation(TopologyappcompleteService.class,
                new TopologyappcompleteServiceImpl(dataBroker,netconfUsername,netconfPsw));
    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
        if (topologyAppInterface != null)
            topologyAppInterface.close();
        LOG.info("TopologyappcompleteProvider Closed");
    }

    private boolean parseTsnPropertiesFile(){
        String currentDir = System.getProperty("user.dir");
        System.out.println("TsntopologyappComplete - Provider: CWD = " + currentDir);
        // When running as a single application the CWD is: tsntopologyappcomplete/karaf/target/assembly/etc/config.properties
        // When running as a feature installed in ODL the CWD is the ODL directory
        // to recognize the different execution, the filename of the path has been used.
        Path path = Paths.get(System.getProperty("user.dir"));
        Path basepath = path.getParent().getParent().getParent();
        if (path.getFileName().toString().equals("assembly"))
            basepath = path.getParent().getParent().getParent();
        else
            basepath = path;

        try (InputStream input = new FileInputStream(basepath+"/complete.properties")) {
            Properties prop = new Properties();
            if (input == null) {
                LOG.error("TsntopologyappComplete - Unable to find complete.properties");
                System.out.println("Unable to find complete.properties file");
                return false;
            }
            prop.load(input);
            // netconf param
            netconfUsername = prop.getProperty("netconf.username");
            netconfPsw = prop.getProperty("netconf.password");
            if(netconfUsername == null || netconfPsw == null){
                System.out.println("TsntopologyappComplete - NETCONF username or NETCONF password not set");
                LOG.error("TsntopologyappComplete - NETCONF username or NETCONF password not set");
                return false;
            }
            System.out.println("NETCONF configs, username: "+ netconfUsername + "  psw: " + netconfPsw);
            // pce param
            pceIp = prop.getProperty("pce.ip");
            pcePort = prop.getProperty("pce.port");
            if(pceIp == null || pcePort == null){
                System.out.println("TsntopologyappComplete - PCE params not set");
                LOG.error("TsntopologyappComplete - PCE params not set");
                return false;
            }
            System.out.println("PCE: ip: "+ pceIp + "  port: " + pcePort);
        } catch (Exception ex) {
            System.out.println("TsntopologyappComplete - Error opening/parsing properties file");
            LOG.error(ex.getMessage());
            return false;
        }
        return true;
    }
}