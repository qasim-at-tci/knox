/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.knox.gateway.webshell;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import de.thetaphi.forbiddenapis.SuppressForbidden;
import org.apache.commons.io.FileUtils;
import org.apache.knox.gateway.audit.api.Action;
import org.apache.knox.gateway.audit.api.ActionOutcome;
import org.apache.knox.gateway.audit.api.Auditor;
import org.apache.knox.gateway.audit.api.ResourceType;
import org.apache.knox.gateway.websockets.WebsocketLogMessages;
import org.eclipse.jetty.io.RuntimeIOException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;


/**
* data structure to store a connection session
*/
public class ConnectionInfo {

    private InputStream inputStream;
    private OutputStream outputStream;
    private PtyProcess ptyProcess;
    private final String username;
    private final Auditor auditor;
    private final WebsocketLogMessages LOG;
    private final String gatewayPIDDir;
    @SuppressWarnings("PMD.DoNotUseThreads") //we need to define a Thread to clean up resources using shutdown hook
    private final Thread shutdownHook;
    private final AtomicInteger concurrentWebshells;
    private long pid;

    @SuppressWarnings("PMD.DoNotUseThreads") //we need to define a Thread to clean up resources using shutdown hook
    public ConnectionInfo(String username, String gatewayPIDDir, AtomicInteger concurrentWebshells, Auditor auditor, WebsocketLogMessages LOG) {
        this.username = username;
        this.auditor = auditor;
        this.LOG = LOG;
        this.gatewayPIDDir = gatewayPIDDir;
        this.concurrentWebshells = concurrentWebshells;
        shutdownHook = new Thread(() -> {
            LOG.debugLog("running webshell shutdown hook");
            disconnect();
        });
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private void saveProcessPID(long pid){
        File file = new File(gatewayPIDDir + "/" + "webshell_" + pid + ".pid");
        try {
            FileUtils.writeStringToFile(file, String.valueOf(pid), StandardCharsets.UTF_8);
        } catch (IOException e){
            LOG.onError("error saving PID for webshell:" + e);
        }
        auditor.audit( Action.WEBSHELL, username+':'+pid,
                ResourceType.PROCESS, ActionOutcome.SUCCESS,"Started Bash process");
    }

    @SuppressForbidden // we need to spawn a bash process for authenticated user
    @SuppressWarnings("PMD.DoNotUseThreads") // we need to define a Thread to register a shutdown hook
    public void connect(){
            // sudoers file needs to be configured for this to work.
            // refer to design doc for details
            String[] cmd = { "sudo","--user", username,"bash"};
            // todo: make environment configurable through gateway-site.xml
            // if do not set environment variable, env = System.getenv() is used by default
            // Map<String,String> env = System.getenv();
            // env.put("TEST_ENV","test_env");
            //env.forEach((key, value) -> LOG.debugLog(key + ":" + value));
            try {
                ptyProcess = new PtyProcessBuilder()
                        .setCommand(cmd)
                        //.setEnvironment(env)
                        .setRedirectErrorStream(true)
                        .setWindowsAnsiColorEnabled(true)
                        .setInitialColumns(150)
                        .setInitialRows(50)
                        .start();
            } catch (IOException e) {
                LOG.onError("Error starting ptyProcess: " + e.getMessage());
                disconnect();
                throw new RuntimeIOException(e);
            }
            outputStream = ptyProcess.getOutputStream();
            inputStream = ptyProcess.getInputStream();
            pid = ptyProcess.pid();
            concurrentWebshells.incrementAndGet();
            saveProcessPID(pid);
    }

    public String getUsername(){
        return this.username;
    }
    public long getPid(){ return this.pid; }
    public InputStream getInputStream(){
        return this.inputStream;
    }
    public OutputStream getOutputStream(){
        return this.outputStream;
    }

    public void disconnect(){
        if (ptyProcess != null) {
            ptyProcess.destroy();
            if (ptyProcess.isAlive()) {
                ptyProcess.destroyForcibly();
            }
        }
        concurrentWebshells.decrementAndGet();
        auditor.audit( Action.WEBSHELL, username+':'+pid,
                ResourceType.PROCESS, ActionOutcome.SUCCESS,"destroyed Bash process");
        File fileToDelete = FileUtils.getFile(gatewayPIDDir + "/" + "webshell_" + pid + ".pid");
        FileUtils.deleteQuietly(fileToDelete);
        try {
            if (inputStream != null) {inputStream.close();}
            if (outputStream != null) {outputStream.close();}
        } catch (IOException e){
            throw new RuntimeIOException(e);
        } finally {
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
        }
    }
}