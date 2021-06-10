package it.nextworks.provisioningappcomplete.impl.provisioning;

import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.AdministrativeState;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.ForwardingDirection;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.OperationalState;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.Uuid;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.global._class.Name;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.global._class.NameBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.CreateConnectivityServiceInput;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connection.*;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.context.Connection;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.context.ConnectionBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.context.ConnectivityService;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.context.ConnectivityServiceBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.service.EndPoint;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.service.EndPointBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.eth.rev200423.*;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.eth.rev200423.context.connectivity.context.connectivity.service.end.point.EthConnectivityServiceEndPointSpecBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TapiConnectivityContextUtils {
    private static final Logger LOG = LoggerFactory.getLogger(TapiConnectivityContextUtils.class);
    private static final String valueNamePathID = "PathID";
    private TapiConnectivityContextUtils() {
        throw new IllegalStateException("Instantiating utility class.");
    }

    // This method takes as argument the list of created CEPs and the UUID of the topLevelConnection,
    // which is linked in the cs. Basically it
    //      1. Creates the top-level connection, from first CEP (over SIP1) to last CEP (over SIP2)
    //              adding also the complete route of CEPs
    //      2. Creates and returns the lower-level connections, which means 1-step connections
    public static List<Connection> createConnections(ArrayList<ConnectionEndPoint> connCepList, Uuid topLevelConnectionUuid){
        List<Connection> lowerConnections = createLowerConnections(connCepList);
        Connection topConnection = createTopLevelConnection(connCepList,lowerConnections,topLevelConnectionUuid);
        lowerConnections.add(topConnection);
        return lowerConnections;
    }

    // Create a Top Level connection between first and last CEPs, with the complete route of CEPs
    // A Top Level connections has the endpoints, direction, route and a list of lower level connection REFs
    private static Connection createTopLevelConnection(ArrayList<ConnectionEndPoint> connCepList, List<Connection> lowerConnections, Uuid topLevelConnectionUuid){
        // Endpoints
        List<ConnectionEndPoint> cepList = new ArrayList<>();
        cepList.add(connCepList.get(0));
        LOG.info("ProvisioningConnectivityContext - createTopLevelConnection: \n First CEP added in TopLevelConnection: "
                +connCepList.get(0).getConnectionEndPointUuid().getValue());
        cepList.add(connCepList.get(connCepList.size()-1));
        LOG.info("ProvisioningConnectivityContext - createTopLevelConnection: \n last CEP added in TopLevelConnection: "
                +connCepList.get(connCepList.size()-1).getConnectionEndPointUuid().getValue());
        // create List of lowerConnection
        List<LowerConnection> lowerConnectionRefList = new ArrayList<>();
        for(Connection lowerConn: lowerConnections){
            lowerConnectionRefList.add(new LowerConnectionBuilder().setConnectionUuid(lowerConn.getUuid()).build());
        }
        ConnectionBuilder connectionBuilder = new ConnectionBuilder()
                .setLowerConnection(lowerConnectionRefList)
                .setConnectionEndPoint(cepList)
                .setUuid(topLevelConnectionUuid)
                .setRoute(createRouteList(connCepList))
                .setDirection(ForwardingDirection.BIDIRECTIONAL);
        return connectionBuilder.build();
    }

    // Create a route with all the CEPs traversed, it is stored in the top level connection
    // the order of the CEP is not maintained in the datastore
    private static List<Route> createRouteList(ArrayList<ConnectionEndPoint> connCepList){
        ArrayList<Route> routes = new ArrayList<>();
        ArrayList<org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.route.ConnectionEndPoint> cepRouteList = new ArrayList<>();
        for(ConnectionEndPoint connCep: connCepList){
            cepRouteList.add(new org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.route.ConnectionEndPointBuilder()
                    .setConnectionEndPointUuid(connCep.getConnectionEndPointUuid())
                    .setNodeEdgePointUuid(connCep.getNodeEdgePointUuid())
                    .setNodeUuid(connCep.getNodeUuid())
                    .setTopologyUuid(connCep.getTopologyUuid())
                    .build());
        }
        routes.add(new RouteBuilder().setConnectionEndPoint(cepRouteList).setLocalId("1").build());
        return routes;
    }

    private static List<Connection> createLowerConnections(ArrayList<ConnectionEndPoint> connCepList){
        List<Connection> connections = new ArrayList<>();
        for(int i=0; i<connCepList.size()-1; i++){
            Uuid connUuid = new Uuid(UUID.randomUUID().toString());
            List<ConnectionEndPoint> cepList = new ArrayList<>();
            cepList.add(connCepList.get(i));
            cepList.add(connCepList.get(i+1));
            LOG.info("ProvisioningConnectivityContext - createLowerConnections \n"+
                    "connection between "+connCepList.get(i).getConnectionEndPointUuid().getValue()
                    +" "+connCepList.get(i+1).getConnectionEndPointUuid().getValue());
            ConnectionBuilder connectionBuilder = new ConnectionBuilder()
                    .setConnectionEndPoint(cepList)
                    .setUuid(connUuid)
                    .setDirection(ForwardingDirection.BIDIRECTIONAL);
            connections.add(connectionBuilder.build());
        }
        return connections;
    }

    public static ConnectivityService createConnectivityService(CreateConnectivityServiceInput input, Uuid topLevelConnectionUuid, Uuid csUuid, String pathId){
        ConnectivityServiceBuilder csBuilder = new ConnectivityServiceBuilder();
        // Endpoints are taken from the connectivity-service creation request
        List<EndPoint> endPointList = new ArrayList<>();

        org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.create.connectivity.service.input.EndPoint inputEp1 = input.getEndPoint().get(0);
        org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.create.connectivity.service.input.EndPoint inputEp2 = input.getEndPoint().get(1);

        // Inizialize new Augmentation with VLAN info, only one augm because it is equal for both endpoints
        EndPoint2 augmInputEp1 = inputEp1.augmentation(EndPoint2.class);
        EthConnectivityServiceEndPointSpecBuilder ethConnSpec1 = new EthConnectivityServiceEndPointSpecBuilder()
                .setEthTerminationCommonPac(augmInputEp1.getEthConnectivityServiceEndPointSpec().getEthTerminationCommonPac());

        EndPointBuilder epBuilder1 = new EndPointBuilder(inputEp1)
                .addAugmentation(EndPoint1.class, new EndPoint1Builder()
                        .setEthConnectivityServiceEndPointSpec(ethConnSpec1.build()).build());
        EndPointBuilder epBuilder2 = new EndPointBuilder(inputEp2)
                .addAugmentation(EndPoint1.class, new EndPoint1Builder()
                        .setEthConnectivityServiceEndPointSpec(ethConnSpec1.build()).build());
        endPointList.add(epBuilder1.build());
        endPointList.add(epBuilder2.build());

        // add REF to top level connection
        List<org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.service.Connection> connections = new ArrayList<>();
        org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.service.ConnectionBuilder connBuilder =
                new org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.service.ConnectionBuilder();
        connBuilder.setConnectionUuid(topLevelConnectionUuid);
        connections.add(connBuilder.build());

        // Build namelist to store the path ID
        List<Name> names = new ArrayList<>();
        names.add(new NameBuilder().setValueName(valueNamePathID).setValue(pathId).build());

        csBuilder.setUuid(csUuid)
                .setEndPoint(endPointList)
                .setName(names)
                .setOperationalState(OperationalState.ENABLED)
                .setAdministrativeState(AdministrativeState.UNLOCKED)
                .setConnection(connections);
        return csBuilder.build();
    }

    public static String searchPathID(List<Name> names){
        if (names!=null){
            for(Name name : names){
                if(name.getValueName().equals(valueNamePathID))
                    return name.getValue();
            }
        }
        return null;
    }
}
