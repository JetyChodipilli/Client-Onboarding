package com.brainserve.clientonboarding.assets.infrastructure;

import com.brainserve.clientonboarding.assets.application.MalwareScanner;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class ClamAvScanner implements MalwareScanner {
    private final AssetProperties settings;
    private final java.util.Timer deadlines=new java.util.Timer("asset-scan-deadlines",true);
    public ClamAvScanner(AssetProperties settings) { this.settings=settings; }
    public Verdict scan(Path file) throws IOException {
        if(Files.size(file)>com.brainserve.clientonboarding.assets.domain.model.AssetPolicy.MAX_BYTES)throw new IOException("File exceeds scan limit");
        try(Socket socket=new Socket()) {
            socket.connect(new InetSocketAddress(settings.getScannerHost(),settings.getScannerPort()),5000);
            socket.setSoTimeout(60000);
            var deadline=new java.util.TimerTask(){public void run(){try{socket.close();}catch(IOException e){org.slf4j.LoggerFactory.getLogger(ClamAvScanner.class).debug("Scanner deadline close failed",e);}}};
            deadlines.schedule(deadline,60000);
            try {
            var out=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            try(var in=Files.newInputStream(file)) {
                byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1) {out.writeInt(count);out.write(buffer,0,count);}
            }
            out.writeInt(0);out.flush();
            var reply=new ByteArrayOutputStream();var in=socket.getInputStream();int value;
            while((value=in.read())!=-1 && value!=0) {if(reply.size()>=4096)throw new IOException("Invalid scanner reply");reply.write(value);}
            if(value!=0) throw new IOException("Incomplete scanner reply");
            String result=reply.toString(StandardCharsets.UTF_8);
            if(result.equals("stream: OK"))return Verdict.CLEAN;
            if(result.startsWith("stream: ") && result.endsWith(" FOUND"))return Verdict.INFECTED;
            throw new IOException("Scanner did not confirm a safe result");
            }finally{deadline.cancel();deadlines.purge();}
        }
    }
    @jakarta.annotation.PreDestroy public void close(){deadlines.cancel();}
}
