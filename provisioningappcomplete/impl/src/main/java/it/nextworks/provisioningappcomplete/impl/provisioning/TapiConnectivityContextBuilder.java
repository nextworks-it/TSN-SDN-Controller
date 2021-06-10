package it.nextworks.provisioningappcomplete.impl.provisioning;

import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.Uuid;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.context.Connection;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.connectivity.context.ConnectivityService;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.context.ConnectivityContext;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.connectivity.rev200616.context.ConnectivityContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TapiConnectivityContextBuilder {
    private static final Logger LOG = LoggerFactory.getLogger(TapiConnectivityContextBuilder.class);
    protected ConnectivityContextBuilder builder;

    public TapiConnectivityContextBuilder(ConnectivityContext ctx){
        if(ctx == null){
            LOG.warn("TapiConnectivityContextBuilder: NULL connectivity context");
            builder = new ConnectivityContextBuilder();
        } else {
            LOG.info("TapiConnectivityContextBuilder: NOT null connectivity context");
            builder = new ConnectivityContextBuilder(ctx);
        }
    }

    public TapiConnectivityContextBuilder addConnectivityService(ConnectivityService cs){
        List<ConnectivityService> csList = builder.getConnectivityService();
        if(csList == null){
            csList = new ArrayList<>();
        }
        csList.add(cs);
        builder.setConnectivityService(csList);
        return this;
    }

    public TapiConnectivityContextBuilder addConnections(List<Connection> connections){
        List<Connection> connectionList = builder.getConnection();
        if(connectionList == null){
            connectionList = new ArrayList<>();
        }
        for(Connection conn: connections)
            connectionList.add(conn);
        builder.setConnection(connectionList);
        return this;
    }

    public ConnectivityService removeConnectivityService(String csUuid){
        List<ConnectivityService> csList = builder.getConnectivityService();
        if(csList != null && !csList.isEmpty()) {
            Iterator iterator = csList.iterator();
            while (iterator.hasNext()) {
                ConnectivityService connectivityService = (ConnectivityService) iterator.next();
                if (connectivityService.getUuid().getValue().equals(csUuid)) {
                    LOG.info("TapiConnectivityContextBuilder - removeConnectivityService: successful removal of connectivity-service " + csUuid);
                    iterator.remove();
                    return connectivityService;
                }
            }
            LOG.error("TapiConnectivityContextBuilder - removeConnectivityService: connectivity-service not found");
            return null;
        }
        LOG.error("TapiConnectivityContextBuilder - removeConnectivityService: empty connectivity-service list");
        return null;
    }

    public TapiConnectivityContextBuilder removeConnections(List<Connection> oldConnections){
        List<Connection> connectionList = builder.getConnection();
        if(connectionList != null && !connectionList.isEmpty()) {
            for (Connection toBeRemoved : oldConnections) {
                Iterator iterator = connectionList.iterator();
                while (iterator.hasNext()) {
                    Connection conn = (Connection) iterator.next();
                    if (conn.getUuid().equals(toBeRemoved.getUuid())) {
                        LOG.info("TapiConnectivityContextBuilder - removeConnections: successful removal of connection " + conn.getUuid().getValue());
                        iterator.remove();
                    }
                }
            }
        } else {
            LOG.error("TapiConnectivityContextBuilder - removeConnections: empty connection list");
        }
        return this;
    }

    public Connection removeConnection(Uuid oldConnectionUuid){
        List<Connection> connectionList = builder.getConnection();
        if(connectionList != null && !connectionList.isEmpty()) {
            Iterator iterator = connectionList.iterator();
            while (iterator.hasNext()) {
                Connection conn = (Connection) iterator.next();
                if (conn.getUuid().equals(oldConnectionUuid)) {
                    LOG.info("TapiConnectivityContextBuilder - removeConnection: successful removal of connection " + conn.getUuid().getValue());
                    iterator.remove();
                    return conn;
                }
            }
            LOG.error("TapiConnectivityContextBuilder - removeConnection: connection with uuid "+oldConnectionUuid+" not found");
        } else
            LOG.error("TapiConnectivityContextBuilder - removeConnection: empty connection list");
        return null;
    }

    public ConnectivityService getConnectivityService(String csUuid){
        List<ConnectivityService> csList = builder.getConnectivityService();
        if(csList != null && !csList.isEmpty()){
            for(ConnectivityService connServ : csList){
                if(connServ.getUuid().getValue().equals(csUuid)){
                    return connServ;
                }
            }
            LOG.error("TapiConnectivityContextBuilder - getConnectivityService: connectivity-service not found");
        } else
            LOG.error("TapiConnectivityContextBuilder - getConnectivityService: empty connectivity-service list");
        return null;
    }

    public ConnectivityContext getConnectivityContext() {
        return builder.build();
    }
}
