/*
 * MIT License
 * Copyright (c) 2017-2019 nuls.io
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.nuls.api;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.nuls.api.core.mongodb.MongoDBService;
import io.nuls.api.core.util.Log;
import io.nuls.api.bean.SpringLiteContext;
import io.nuls.api.jsonrpc.JsonRpcServer;
import io.nuls.sdk.core.utils.RestFulUtils;
import io.nuls.sdk.core.utils.StringUtils;
import io.nuls.api.utils.ConfigLoader;

import java.util.Properties;

/**
 * @author Niels
 */
public class ApiModuleBootstrap {

    public static void main(String[] args) {
        String ip = "0.0.0.0";
        int port = 8080;
        String walletUrl = "";
        String dbIp = "127.0.0.1";
        int dbPort = 27017;
        String dbName = "nuls";
        try {
            Properties prop = ConfigLoader.loadProperties("cfg.properties");
            String ipOfCfg = prop.getProperty("listener.ip");
            if (!StringUtils.isBlank(ipOfCfg)) {
                ip = ipOfCfg;
            }
            String portOfCfg = prop.getProperty("listener.port");
            if (StringUtils.isNotBlank(portOfCfg)) {
                port = Integer.parseInt(portOfCfg);
            }
            walletUrl = prop.getProperty("wallet.url");
        } catch (Exception e) {
            Log.error(e);
        }
        RestFulUtils.getInstance().setServerUri(walletUrl);

        MongoClient mongoClient = new MongoClient(dbIp, dbPort);
        MongoDatabase mongoDatabase = mongoClient.getDatabase(dbName);
        MongoDBService dbService = new MongoDBService(mongoDatabase);
        SpringLiteContext.putBean("dbService", dbService);

        SpringLiteContext.init("io.nuls");


        JsonRpcServer server = new JsonRpcServer();

        server.startServer(ip, port);
    }
}