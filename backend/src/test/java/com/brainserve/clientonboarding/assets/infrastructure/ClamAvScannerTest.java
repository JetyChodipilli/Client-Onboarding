package com.brainserve.clientonboarding.assets.infrastructure;

import static org.assertj.core.api.Assertions.*;
import com.brainserve.clientonboarding.assets.application.MalwareScanner;
import java.io.*;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class ClamAvScannerTest {
    @Test void protocolRequiresCompleteExplicitVerdictAndSendsFramedBytes()throws Exception {
        assertThat(scan("stream: OK\0")).isEqualTo(MalwareScanner.Verdict.CLEAN);
        assertThat(scan("stream: Eicar-Signature FOUND\0")).isEqualTo(MalwareScanner.Verdict.INFECTED);
        for(String reply:new String[]{"stream: OK","stream: scan failed ERROR\0","OK\0"})
            assertThatThrownBy(()->scan(reply)).isInstanceOf(IOException.class);
    }
    private MalwareScanner.Verdict scan(String response)throws Exception {
        var path=Files.createTempFile("scanner-test-",".txt");Files.writeString(path,"test content");
        try(var server=new ServerSocket(0)) {
            var task=new java.util.concurrent.FutureTask<Void>(()->{
                try(var socket=server.accept()) {
                    var in=new DataInputStream(socket.getInputStream());assertThat(new String(in.readNBytes(10),StandardCharsets.US_ASCII)).isEqualTo("zINSTREAM\0");
                    int size=in.readInt();assertThat(new String(in.readNBytes(size),StandardCharsets.UTF_8)).isEqualTo("test content");assertThat(in.readInt()).isZero();
                    socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));socket.getOutputStream().flush();
                }return null;
            });
            var thread=new Thread(task);thread.setDaemon(true);thread.start();var settings=new AssetProperties();settings.setScannerPort(server.getLocalPort());var scanner=new ClamAvScanner(settings);
            try{return scanner.scan(path);}finally{scanner.close();task.get(5,java.util.concurrent.TimeUnit.SECONDS);}
        }finally{Files.deleteIfExists(path);}
    }
}
