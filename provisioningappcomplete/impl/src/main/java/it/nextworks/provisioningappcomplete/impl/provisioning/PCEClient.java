package it.nextworks.provisioningappcomplete.impl.provisioning;

import it.nextworks.provisioningappcomplete.impl.provisioning.exceptions.PathComputationException;
import it.nextworks.provisioningappcomplete.impl.tapi.TapiUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.ForwardingDirection;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.common.rev200423.Uuid;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.path.computation.rev200423.path.Link;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.path.computation.rev200423.path.LinkBuilder;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.path.computation.rev200423.path.computation.context.Path;
import org.opendaylight.yang.gen.v1.urn.onf.otcc.yang.tapi.path.computation.rev200423.path.computation.context.PathBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class PCEClient {
    private static final Logger LOG = LoggerFactory.getLogger(TapiUtils.class);
    private String PCEServerIP;
    private String PCEServerPort;
    private String computeP2PPathURL = "/operations/tapi-path-computation:compute-p-2-p-path/";
    private String deleteP2PPathURL = "/operations/tapi-path-computation:delete-p-2-p-path/";
    // path /data/tapi-common:context/tapi-path-computation:path-computation-context/path=1907188993/
    private String getP2PPathURL = "/data/tapi-common:context/tapi-path-computation:path-computation-context/";

    public PCEClient(String PCEServerIP, String PCEServerPort) {
        this.PCEServerIP = PCEServerIP;
        this.PCEServerPort = PCEServerPort;
    }

    // *******
    // CREATE P2P PATH
    // *******
    public Path createP2PPath(String sip1, String sip2) throws PathComputationException {
        // 1. Create jsonBody to createPathComputation
        String jsonBody = buildPathComputationCreateInput(sip1,sip2);
        LOG.info("PCEClient - createP2PPath Input JSON of createP2PPath: "+jsonBody);
        // 2. Make request to PCE server
        String jsonOutput = createPath(jsonBody);
        LOG.info("PCEClient - createP2PPath response "+jsonOutput);
        // 3. Parse response to extract the path-uuid, if existing
        if(jsonOutput == null) {
            throw new PathComputationException("Error creating the path");
        }
        String pathId = parsePathComputationCreateOutput(jsonOutput);

        // 4. Make request to retrieve the path to PCE server
        if(pathId == null){
            LOG.error("PCEClient - createP2PPath PathUUID not found in the response");
            throw new PathComputationException("PathUUID not found in the PCE response");
        }
        LOG.info("PCEClient - createP2PPath PathUUID: "+pathId);

        // 4. Parse the response and return path
        return getP2PPath(pathId);

    }


    // Retrieve the pathUuid, sent in the response of the creation
    // for now it returns the first computed pathuuid
    // example "{\"output\": { \"service\": {\"uuid\": null, \"path\": [{\"path-uuid\": \"1072127647\"}],"+
    //            " \"end-point\": [{ \"local-id\": \"1\", \"service-interface-point\": { \"service-interface-point-uuid\": \"nodeA-if1-SIP\" },"+
    //            "\"direction\": \"BIDIRECTIONAL\" },{ \"local-id\": \"2\", \"service-interface-point\": "+
    //            "{ \"service-interface-point-uuid\": \"nodeC-if2-SIP\" },\"direction\": \"BIDIRECTIONAL\" }]}}}"
    private String parsePathComputationCreateOutput(String jsonBody){
        JSONObject jsonObject = new JSONObject(jsonBody);
        JSONObject output = jsonObject.getJSONObject("output");
        JSONObject service = output.getJSONObject("service");
        JSONArray paths = service.getJSONArray("path");
        if(paths.length()>0){
            return paths.getJSONObject(0).getString("path-uuid");
        }
        return null;
    }

    // Method to create input for createP2PPath
    // example "{\"input\": {\"sep\": [{\"local-id\": \"1\",\"service-interface-point\": {\"service-interface-point-uuid\": " +
    //            "\"nodeA-if1-SIP\"},\"direction\": \"BIDIRECTIONAL\"},{\"local-id\": \"2\",\"service-interface-point\": " +
    //            "{\"service-interface-point-uuid\": \"nodeC-if2-SIP\"},\"direction\": \"BIDIRECTIONAL\"}]}}"
    private String buildPathComputationCreateInput(String sip1, String sip2){
        JSONObject endpoint1 = new JSONObject()
                .put("local-id","1")
                .put("direction","BIDIRECTIONAL")
                .put("service-interface-point", new JSONObject()
                        .put("service-interface-point-uuid", sip1));
        JSONObject endpoint2 = new JSONObject()
                .put("local-id","2")
                .put("direction","BIDIRECTIONAL")
                .put("service-interface-point", new JSONObject()
                        .put("service-interface-point-uuid", sip2));
        JSONArray sepList = new JSONArray()
                .put(endpoint1)
                .put(endpoint2);
        JSONObject sep = new JSONObject()
                .put("sep",sepList);
        JSONObject input = new JSONObject()
                .put("input",sep);
        return input.toString();
    }


//    private String createPath(String jsonBodyRequest){
//        return null;
//    }

    private String createPath(String jsonBodyRequest){
        HttpURLConnection insecureconnection = null;
        OutputStream os = null;
        BufferedReader br = null;
        URL url = null;
        int responseCode = 0;

        String urlString = "http://"+PCEServerIP+":"+PCEServerPort+computeP2PPathURL;
        LOG.info("PCEClient - createP2PPath URL: "+urlString);
        try {
            url = new URL(urlString);
            insecureconnection = (HttpURLConnection) url.openConnection();
            insecureconnection.setConnectTimeout(5000);
            insecureconnection.setReadTimeout(5000);
            insecureconnection.setRequestMethod("POST");
            insecureconnection.setRequestProperty("Content-Type", "application/yang-data+json");
            insecureconnection.setRequestProperty("Accept", "application/yang-data+json");
            insecureconnection.setDoOutput(true);

            os = insecureconnection.getOutputStream();
            byte[] input = jsonBodyRequest.getBytes("utf-8");
            os.write(input, 0, input.length);

            // Send request
            responseCode = insecureconnection.getResponseCode();
            LOG.info("PCEClient - createP2PPath POST createP2PPath -> ResponseCode: " + responseCode);

            if (os != null) os.close();
            if (br != null) br.close();

            // Retrieve the POST response
            if (responseCode == HttpURLConnection.HTTP_OK) { // success
                BufferedReader in = new BufferedReader(new InputStreamReader(insecureconnection.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                // close inputStream
                insecureconnection.getInputStream().close();
                return response.toString();
            } else {
                return null;
            }
        } catch (IOException e) {
            LOG.error(e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // *******
    // GET P2P PATH
    // *******
    public Path getP2PPath(String pathId){
        String jsonBody = getPath(pathId);
        LOG.info("getP2PPath json Response "+jsonBody);
        if(jsonBody == null){
            return null;
        }
        return parsePathComputationGetOutput(jsonBody);
    }

    // The output is "{\"path\": {\"name\": null,\"uuid\": \"1072127650\",\"layer-protocol-name\": null,\"link\": [{\"topology-uuid\": " +
    //                "\"topology\",\"link-uuid\": \"linkA-B\"},{\"topology-uuid\": \"topology\",\"link-uuid\": \"linkB-C\"}]," +
    //                "\"routing-constraint\": null,\"direction\": \"UNIDIRECTIONAL\"}}"
    private Path parsePathComputationGetOutput(String jsonBody){
        PathBuilder pathBuilder = new PathBuilder();

        JSONObject jsonOutput = new JSONObject(jsonBody);
        JSONObject jsonPath = jsonOutput.getJSONObject("path");
        JSONArray jsonLinks = jsonPath.getJSONArray("link");

        // Build list of links
        ArrayList<Link> linkList = new ArrayList<>();
        for (int i=0; i < jsonLinks.length(); i++) {
            JSONObject jsonLink = jsonLinks.getJSONObject(i);
            Link link = new LinkBuilder()
                    .setTopologyUuid(new Uuid(jsonLink.getString("topology-uuid")))
                    .setLinkUuid(new Uuid(jsonLink.getString("link-uuid")))
                    .build();
            linkList.add(link);
        }
        pathBuilder
                .setLink(linkList)
                .setDirection(ForwardingDirection.valueOf(jsonPath.getString("direction")))
                .setUuid(new Uuid(jsonPath.getString("uuid")));
        return pathBuilder.build();
    }

    private String getPath(String pathId){
        HttpURLConnection insecureconnection = null;
        OutputStream os = null;
        BufferedReader br = null;
        URL url = null;
        int responseCode = 0;

        String urlString = "http://"+PCEServerIP+":"+PCEServerPort+getP2PPathURL+"path="+pathId+"/";
        LOG.info("PCEClient - getP2PPath URL: "+urlString);
        try {
            url = new URL(urlString);
            insecureconnection = (HttpURLConnection) url.openConnection();
            insecureconnection.setConnectTimeout(5000);
            insecureconnection.setReadTimeout(5000);
            insecureconnection.setRequestMethod("GET");
            insecureconnection.setRequestProperty("Content-Type", "application/yang-data+json");
            insecureconnection.setRequestProperty("Accept", "application/yang-data+json");

            // Send request
            responseCode = insecureconnection.getResponseCode();
            LOG.info("PCEClient - getP2PPath GET getP2PPath -> ResponseCode: " + responseCode);

            if (os != null) os.close();
            if (br != null) br.close();

            // Retrieve the POST response
            if (responseCode == HttpURLConnection.HTTP_OK) { // success
                BufferedReader in = new BufferedReader(new InputStreamReader(insecureconnection.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                // close inputStream
                insecureconnection.getInputStream().close();
                return response.toString();
            } else {
                return null;
            }
        } catch (IOException e) {
            LOG.error(e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // *******
    // DELETE P2P PATH
    // *******
    public boolean deleteP2PPath(String pathId){
        // 1. Build the input json
        String jsonInput = buildPathComputationDeleteInput(pathId);
        LOG.info("PCEClient - deleteP2PPath jsonInput "+ jsonInput);
        // 2. Make request
        Boolean output = deletePath(jsonInput);
        return false;
    }

    // Create the input of the request to delete a path
    // example: "{\"input\":{\"path-id-or-name\":\"1072127647\"}}";
    private String buildPathComputationDeleteInput(String pathId){
        JSONObject jsonInput = new JSONObject()
                .put("input", new JSONObject()
                        .put("path-id-or-name",pathId));
        return jsonInput.toString();
    }

    // output is: "{\"output\": null}"
    private boolean deletePath(String jsonBodyRequest){
        HttpURLConnection insecureconnection = null;
        OutputStream os = null;
        BufferedReader br = null;
        URL url = null;
        int responseCode = 0;

        String urlString = "http://"+PCEServerIP+":"+PCEServerPort+deleteP2PPathURL;
        LOG.info("PCEClient - deleteP2PPath URL: "+urlString);
        try {
            url = new URL(urlString);
            insecureconnection = (HttpURLConnection) url.openConnection();
            insecureconnection.setConnectTimeout(5000);
            insecureconnection.setReadTimeout(5000);
            insecureconnection.setRequestMethod("POST");
            insecureconnection.setRequestProperty("Content-Type", "application/yang-data+json");
            insecureconnection.setRequestProperty("Accept", "application/yang-data+json");
            insecureconnection.setDoOutput(true);

            os = insecureconnection.getOutputStream();
            byte[] input = jsonBodyRequest.getBytes("utf-8");
            os.write(input, 0, input.length);

            // Send request
            responseCode = insecureconnection.getResponseCode();
            LOG.info("PCEClient - deleteP2PPath POST deletePath -> ResponseCode: " + responseCode);

            if (os != null) os.close();
            if (br != null) br.close();

            if (responseCode >= 400) { // error
                return false;
            } else {
                return true;
            }
        } catch (IOException e) {
            LOG.error(e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

}
