import java.io.IOException;
import java.io.OutputStream;
import java.io.*;
import java.net.InetSocketAddress;
import java.util.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.sql.*;
import java.net.http.HttpClient;

public class StockService {

    static private int OPERATION_TYPE_FILL = 1;
    static private int OPERATION_TYPE_ACCQUIRE = 2;
    static private int OPERATION_TYPE_RELEASE = 3;
    static Connection connection = null;

    public static void main(String[] args) throws Exception {
        String version = args[5];
        String host = args[0];
        String port = args[1];
        String user = args[2];
        String password = args[3];
        String db = args[4];
        System.out.println("Hardcode version: v11");
        System.out.println("Config version: " + version);
        System.out.println(host);
        System.out.println(port);
        System.out.println(user);
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new MyHandler());
        server.setExecutor(null); // creates a default executor
	    Class.forName("com.mysql.cj.jdbc.Driver");
        connection = DriverManager.getConnection("jdbc:mysql://" + host + ":"+port + "/"+ db, user, password);
        server.start();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            System.out.println("Request accepted");
            String path = t.getRequestURI().getPath();
            System.out.println("Path: " + path);
            if ("/health".equals(path)) {
                routeHealth(t);
                System.out.println("matched");
            } else if ("/accquire-item".equals(path)) { // by catalog id
                accquireItem(t);
                System.out.println("accquireItem");
            } else if ("/release-order-items".equals(path)) { // by orderId
                releaseOrderItems(t);
                System.out.println("release-order-items");
            } else if ("/release-item".equals(path)) { // by catalog id
                releaseItem(t);
                System.out.println("release-item");
            } else if ("/fill-item".equals(path)) { // by catalog id
                fillItem(t);
                System.out.println("fill-item");
            } else if ("/items-count".equals(path)) {
                itemsCount(t);
                System.out.println("matched");
            } else if ("/create-catalog-item".equals(path)) {
                createCataologItem(t);
                System.out.println("matched");
            } else if ("/catalog".equals(path)) {
                catalog(t);
                System.out.println("matched");
            } else {
                    String response = "{\"status\": \"not found\"}";
                    t.sendResponseHeaders(404, response.length());
                    OutputStream os = t.getResponseBody();
                    os.write(response.getBytes());
                    os.close();
                    System.out.println("not matched");
                }
            }
    }


    static private void routeHealth(HttpExchange t) throws IOException {
        System.out.println("Request accepted");
        String response = "{\"status\": \"OK\"}";
        t.sendResponseHeaders(200, response.length());
        OutputStream os = t.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    static private void accquireItem(HttpExchange t) throws IOException {
        System.out.println("accquireItem accepted");
        String r;
        try {
            Map<String, String> q = postToMap(buf(t.getRequestBody()));
            String operationType = OPERATION_TYPE_ACCQUIRE + "";
            String catalogId = q.get("catalog_id");
            String cnt = q.get("count");
            String orderId = q.get("order_id");
            String requestId = q.get("request_id");
            String sql = "select * from stock where request_id = " + requestId;
            Statement stmt=connection.createStatement();
            ResultSet rs=stmt.executeQuery(sql);
            r = "";
            if (rs.next()) {
                r = "duplicate request";
                System.out.println("send headers");
                t.sendResponseHeaders(409, r.length());
                System.out.println("duplicate request");
                return;
            }
            stmt=connection.createStatement();
            sql = "insert into stock (catalog_id, operation_type, order_id, cnt, request_id) values (" + catalogId + ", " + operationType + ", " + orderId + ", -" + cnt + ", \"" + requestId + "\")";
            System.out.println("request to database: " + sql);
            stmt.executeUpdate(sql);
            r = "";
            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            r = "internal server error";
            t.sendResponseHeaders(500, r.length());
        }
        OutputStream os = t.getResponseBody();
        os.write(r.getBytes());
        os.close();
    }

    static private String getStatusById(int status) {
        if (status == 2) return "In progress";
        if (status == 3) return "Done";
        return "Created";
    }

    static private void fillItem(HttpExchange t) throws IOException {
        System.out.println("fillItem request accepted");
        String r;
        System.out.println("start try-catch");
        try {
            Map<String, String> q = postToMap(buf(t.getRequestBody()));
            System.out.println("postToMap done");
            String operationType = OPERATION_TYPE_FILL + "";
            String catalogId = q.get("catalog_id");
            String cnt = q.get("count");
            String requestId = q.get("request_id");

            String sql = "select * from stock where request_id = \"" + requestId + "\"";
            Statement stmt=connection.createStatement();
            ResultSet rs=stmt.executeQuery(sql);
            System.out.println("fillItem select sql: " + sql);
            r = "";
            if (rs.next()) {
                r = "duplicate request";
                System.out.println("send headers");
                t.sendResponseHeaders(409, r.length());
                System.out.println("duplicate request");
                return;
            }

            stmt=connection.createStatement();
            sql = "insert into stock (catalog_id, operation_type, order_id, cnt, request_id) values (" + catalogId + ", " + operationType + ", null, " + cnt + ", \"" + requestId + "\")";
            System.out.println("fill item insert to database: " + sql);
            stmt.executeUpdate(sql);
            r = "";
            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
            System.out.println("success");
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            r = "internal server error";
            t.sendResponseHeaders(500, r.length());
        }
        System.out.println("send body");
        OutputStream os = t.getResponseBody();
        os.write(r.getBytes());
        os.close();
    }

    static private void commitTransaction() {
        try {
            connection.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    static private void rollbackTransaction() {
        try {
            connection.rollback();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    static private void releaseOrderItems(HttpExchange t) throws IOException {
        System.out.println("releaseOrderItems");
        try {
            Map<String, String> q = postToMap(buf(t.getRequestBody()));
            String orderId = q.get("order_id");
            String sql = "select order_id from cancelled_orders where order_id = " + orderId;
            Statement stmt=connection.createStatement();
            ResultSet rs=stmt.executeQuery(sql);
            String r = "";
            if (rs.next()) {
                r = "";
                System.out.println("send headers");
                t.sendResponseHeaders(409, r.length());
                System.out.println("success");
                return;
            }
            stmt=connection.createStatement();
            connection.setAutoCommit(false);
            sql = "insert into cancelled_orders (order_id) values (" + orderId + ")";
            System.out.println("sql: " + sql);
            stmt.executeUpdate(sql);
            sql = "select -sum(cnt) total_cnt, catalog_id, order_id from stock where order_id = " + orderId + " group by item_id, order_id";
            List<String> valuesToInsert = new ArrayList<>();
            String values = "";
            while (rs.next()) {
                String cnt = "" + rs.getInt(1);
                String itemId = "" + rs.getString(2);
                values = "(" + itemId + ", " + cnt + ", " + orderId + ", " + OPERATION_TYPE_RELEASE + ")";
                valuesToInsert.add(values);
            }

            if (valuesToInsert.size() > 0) {
                String valuesToInsertSql = String.join(", ", values);
                sql = "insert into stock (catalog_id, cnt, order_id, operation_type) values " + valuesToInsertSql;
                stmt.executeUpdate(sql);
            }
            commitTransaction();
            r = "";
            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
            System.out.println("success");
        } catch (Throwable e) {
            rollbackTransaction();
            System.out.println("error: " + e.getMessage());
            String r = "bad request";
            t.sendResponseHeaders(400, r.length());
            OutputStream os = t.getResponseBody();
            os.write(r.getBytes());
            os.close();
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

        }
    }

    static private void releaseItem(HttpExchange t) throws IOException {
        System.out.println("releaseItem request accepted");
        String r;
        System.out.println("start try-catch");
        try {
            Map<String, String> q = postToMap(buf(t.getRequestBody()));
            System.out.println("postToMap done");
            String operationType = OPERATION_TYPE_RELEASE + "";
            String catalogId = q.get("catalog_id");
            int cnt = Integer.valueOf(q.get("count"));
            String orderId = q.get("order_id");
            String requestId = q.get("request_id");

            Statement _stmt=connection.createStatement();
            String selectSql = "select * from stock where request_id = \"" + requestId + " \"";
            System.out.println("releaseItem request_id, select sql: " + selectSql);
            ResultSet rs=_stmt.executeQuery(selectSql);
            if (rs.next()) {
                r = "duplicate request";
                t.sendResponseHeaders(409, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            Statement __stmt=connection.createStatement();
            selectSql = "select sum(cnt) total_cnt from stock where order_id = " + orderId + "";
            System.out.println("releaseItem order_id, select sql: " + selectSql);
            ResultSet _rs = __stmt.executeQuery(selectSql);
            if (_rs.next()) {
                int totalCnt = _rs.getInt(1);
                if (totalCnt < cnt) {
                    r = "not enough count";
                    t.sendResponseHeaders(409, r.length());
                    System.out.println(r);
                    OutputStream os = t.getResponseBody();
                    os.write(r.getBytes());
                    os.close();
                    return;
                }
            }

            Statement stmt=connection.createStatement();
            String sql = "insert into stock (catalog_id, operation_type, order_id, cnt, request_id) values (" + catalogId + ", " + operationType + ", " + orderId + ", -" + cnt + ", \"" + requestId + "\")";
            System.out.println("request to database: " + sql);
            stmt.executeUpdate(sql);
            r = "";
            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
            System.out.println("success");
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            r = "internal server error";
            t.sendResponseHeaders(500, r.length());
        }
        System.out.println("send body");
        OutputStream os = t.getResponseBody();
        os.write(r.getBytes());
        os.close();

    }

    static private void itemsCount(HttpExchange t) throws IOException{
        System.out.println("Read request accepted");
        String r;
        try {
            Statement stmt=connection.createStatement();
            ResultSet rs=stmt.executeQuery("select catalog_id, sum(cnt) total_cnt from stock group by catalog_id");
            List<String> items = new ArrayList<>();
            while (rs.next()) {
                String itemId = "" + rs.getInt(1);
                String cnt = rs.getString(2);
                r = "item:" + itemId + ",cnt:" + itemId;
                items.add(r);
            }
            r = String.join("\n", items);
            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
            System.out.println("success");
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            r = "internal server error";
            t.sendResponseHeaders(500, r.length());
        }
        OutputStream os = t.getResponseBody();
        os.write(r.getBytes());
        os.close();
    }

    static private void catalog(HttpExchange t) throws IOException{
        System.out.println("Read catalog request accepted");
        String r;
        try {
            Statement stmt=connection.createStatement();
            ResultSet rs=stmt.executeQuery("select id, good_code, good_name, good_description, measurement_units, price_per_unit from catalog");
            List<String> items = new ArrayList<>();
            while (rs.next()) {
                String id = "" + rs.getInt(1);
                String goodCode = rs.getString(2);
                String goodName = "" + rs.getString(3);
                String goodDescription = rs.getString(4);
                String measurementUnits = "" + rs.getString(5);
                String pricePerUnit = getStatusById(rs.getInt(6));
                r = "id:" + id + ",goodCode:" + goodCode + ",goodName:" + goodName + ",goodDescription:" + goodDescription + ",measurementUnits:" + measurementUnits + ",pricePerUnit:" + pricePerUnit;
                items.add(r);
            }
            r = String.join("\n", items);
            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
            System.out.println("success");
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            r = "internal server error";
            t.sendResponseHeaders(500, r.length());
        }
        OutputStream os = t.getResponseBody();
        os.write(r.getBytes());
        os.close();
    }

    static private void createCataologItem(HttpExchange t) throws IOException {
        System.out.println("routeCreateGood request accepted");
        String r;
        System.out.println("start try-catch");
        try {
            Map<String, String> q = postToMap(buf(t.getRequestBody()));
            System.out.println("postToMap done");
            String good_code = q.get("good_code");
            String good_name = q.get("good_name");
            String good_description = q.get("good_description");
            String measurement_units = q.get("measurement_units");
            String price_per_unit = q.get("price_per_unit");
            Statement _stmt=connection.createStatement();
            String selectSql = "select * from catalog where good_code = \"" + good_code + " \"";
            System.out.println("routeCreateGood request accepted, select sql: " + selectSql);
            ResultSet rs=_stmt.executeQuery(selectSql);
            if (rs.next()) {
                r = "good_allready_exists";
                t.sendResponseHeaders(409, r.length());
                System.out.println(r);
                OutputStream os = t.getResponseBody();
                os.write(r.getBytes());
                os.close();
                return;
            }

            Statement stmt=connection.createStatement();
            String sql = "insert into catalog (good_code, good_name, good_description, measurement_units, price_per_unit) values (\"" + good_code + "\", \"" + good_name + "\", \"" + good_description + "\", \"" + measurement_units + "\", " + price_per_unit + ")";
            System.out.println("request to database: " + sql);
            stmt.executeUpdate(sql);
            r = "";
            System.out.println("send headers");
            t.sendResponseHeaders(200, r.length());
            System.out.println("success");
        } catch (Throwable e) {
            System.out.println("error: " + e.getMessage());
            r = "internal server error";
            t.sendResponseHeaders(500, r.length());
        }
        System.out.println("send body");
        OutputStream os = t.getResponseBody();
        os.write(r.getBytes());
        os.close();
    }

    static private Map<String, String> queryToMap(String query) {
        if(query == null) {
            return new HashMap<>();
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            }else{
                result.put(entry[0], "");
            }
        }
        return result;
    }

    static private Map<String, String> postToMap(StringBuilder body){
        String[] parts = body
                .toString()
                .replaceAll("\r", "")
                .split("\n");
        Map<String, String> result = new HashMap<>();
        for (String part: parts) {
            String[] keyVal = part.split(":");
            result.put(keyVal[0], keyVal[1]);
        }
        System.out.println("postToMap: " + result.toString());
        return result;
    }

    static private List<List<String>> postToList(StringBuilder body){
        String[] parts = body
                .toString()
                .replaceAll("\r", "")
                .split("\n");
        List<List<String>> result = new ArrayList<>();
        for (String part: parts) {
            String[] keyVal = part.split(":");
            List<String> l = new ArrayList<>();
            l.add(0, keyVal[0]);
            l.add(1, keyVal[1]);
            result.add(l);
        }
        System.out.println("postToList: " + result.toString());
        return result;
    }

    static private StringBuilder buf(InputStream inp)  throws UnsupportedEncodingException, IOException {
        InputStreamReader isr =  new InputStreamReader(inp,"utf-8");
        BufferedReader br = new BufferedReader(isr);
        int b;
        StringBuilder buf = new StringBuilder(512);
        while ((b = br.read()) != -1) {
            buf.append((char) b);
        }
        br.close();
        isr.close();
        System.out.println("buf : " + buf);
        return buf;
    }
}