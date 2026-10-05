import java.lang.reflect.Method;
import java.net.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Boots the real production Fabric/Mixin loader without running Minecraft's graphical main or creating a world. */
public final class ProductionSmoke {
    private static int protocol;
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void main(String[] args) throws Exception {
        try {
            String side = args[0];
            protocol = args[2].equals("1.20.1") ? 763 : 767;
            boolean zstd = Boolean.parseBoolean(args[1]);
            Class<? extends Enum> environment = (Class<? extends Enum>) Class.forName("net.fabricmc.api.EnvType");
            Class<?> knotClass = Class.forName("net.fabricmc.loader.impl.launch.knot.Knot");
            Object knot = knotClass.getConstructor(environment).newInstance(Enum.valueOf(environment, side));
            ClassLoader loader = (ClassLoader) knotClass.getMethod("init", String[].class).invoke(knot,
                    (Object) new String[]{"--version", args[2], "--gameDir", args[3], "--accessToken", "0"});
            Thread.currentThread().setContextClassLoader(loader);
            // Minecraft main normally initializes SharedConstants before constructing ServerData.
            Class.forName("net.minecraft.class_155", true, loader).getMethod("method_36208").invoke(null);
            Class<?> entry = Class.forName("io.github.therealkamisama.miguelnetwork.MiguelNetwork", true, loader);
            entry.getMethod("onInitialize").invoke(entry.getConstructor().newInstance());
            if (side.equals("CLIENT")) {
                Class<?> client = Class.forName("io.github.therealkamisama.miguelnetwork.client.MiguelNetworkClient", true, loader);
                client.getMethod("onInitializeClient").invoke(client.getConstructor().newInstance());
                Class.forName("net.minecraft.class_2535", false, loader);
                if (zstd) {
                    Class.forName("cn.tohsaka.factory.zstdnet.coremod.ConnectScreenHooks", true, loader);
                    Class<?> plugin = Class.forName("io.github.therealkamisama.miguelnetwork.compat.ZstdNetMixinPlugin", true, loader);
                    if (!Boolean.TRUE.equals(plugin.getMethod("isApplied").invoke(null)))
                        throw new AssertionError("ZstdNet production Mixin did not apply");
                }
                transfer(loader, Path.of(args[3]), zstd, false);
                if (zstd) transfer(loader, Path.of(args[3]), true, true);
            }
            System.out.println("MIGUELNETWORK_PRODUCTION_SMOKE_OK " + args[2] + " " + side + " zstd=" + zstd);
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }

    private static Class<?> type(ClassLoader loader, String suffix) throws Exception {
        return Class.forName("io.github.therealkamisama.miguelnetwork." + suffix, true, loader);
    }
    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }
    private static Object call(Class<?> owner, String name, Object receiver, Object... args) throws Exception {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                method.setAccessible(true);
                return method.invoke(receiver, args);
            }
        }
        throw new NoSuchMethodException(owner + "." + name);
    }
    private static int varint(InputStream input) throws Exception {
        int value=0;
        for(int shift=0;shift<35;shift+=7) { int b=input.read(); if(b<0)throw new EOFException(); value|=(b&127)<<shift; if((b&128)==0)return value; }
        throw new IOException("Invalid VarInt");
    }
    private static void varint(OutputStream output,int value) throws Exception {
        do {int b=value&127; value >>>=7; output.write(value==0?b:b|128);}while(value!=0);
    }
    private static byte[] handshake(String host, int port) throws Exception {
        ByteArrayOutputStream payload=new ByteArrayOutputStream();
        varint(payload,0); varint(payload,protocol); byte[] name=host.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        varint(payload,name.length); payload.write(name); new DataOutputStream(payload).writeShort(port); varint(payload,2);
        ByteArrayOutputStream packet=new ByteArrayOutputStream(); varint(packet,payload.size()); payload.writeTo(packet); return packet.toByteArray();
    }
    private static void transfer(ClassLoader loader, Path instance, boolean zstdClient, boolean compressed) throws Exception {
        String logicalHost="127.0.0.2";
        int publicPort=freePort();
        System.setProperty("miguelnetwork.bindHost",logicalHost);
        System.setProperty("miguelnetwork.public.port",Integer.toString(publicPort));
        byte[] payload=new byte[32768]; new Random(42).nextBytes(payload);
        byte[] handshake=handshake(logicalHost,publicPort);
        Class<?> controller=type(loader,"server.ServerTunnelController");
        Class<?> manager=type(loader,"client.ClientTunnelManager");
        Object zstdServer=null, proxy=null;
        Class<?> runtime=null;
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try (ServerSocket backend=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"))) {
            backend.setSoTimeout(20000);
            Future<?> received=executor.submit(() -> {
                try (Socket socket=backend.accept()) {
                    socket.setSoTimeout(20000);
                    InputStream input=socket.getInputStream();
                    int length=varint(input);
                    byte[] packet=input.readNBytes(length);
                    ByteArrayOutputStream framed=new ByteArrayOutputStream(); varint(framed,length); framed.write(packet);
                    if (!compressed) {
                        if(!Arrays.equals(handshake,framed.toByteArray()))throw new AssertionError("Handshake host/port changed");
                    } else {
                        InputStream decoded = new ByteArrayInputStream(packet);
                        if(varint(decoded)!=0 || varint(decoded)!=protocol)throw new AssertionError("Handshake prefix changed");
                        String forwardedHost=new String(decoded.readNBytes(varint(decoded)),java.nio.charset.StandardCharsets.UTF_8);
                        // ZstdNet deliberately appends its real-IP extension after the logical host.
                        if(!forwardedHost.split("\u0000",2)[0].equals(logicalHost)
                                || new DataInputStream(decoded).readUnsignedShort()!=publicPort || varint(decoded)!=2)
                            throw new AssertionError("Logical handshake host/port changed");
                    }
                    if(!Arrays.equals(payload,input.readNBytes(payload.length)))throw new AssertionError("Upstream bytes changed");
                    socket.getOutputStream().write(payload); socket.getOutputStream().flush();
                }catch(Exception e){throw new RuntimeException(e);}
            });
            Integer zstdPort=null;
            if(compressed) {
                zstdPort=freePort();
                Files.writeString(instance.resolve("config/zstdnet-server.properties"),
                    "enabled=true\nlisten=127.0.0.1:"+zstdPort+"\ntarget=127.0.0.1:"+backend.getLocalPort()+"\nudp_direct_ports=\n");
                runtime=Class.forName("cn.tohsaka.factory.zstdnet.server.ServerProxyRuntime",true,loader);
                var constructor=runtime.getDeclaredConstructor(); constructor.setAccessible(true); zstdServer=constructor.newInstance();
                call(runtime,"start",zstdServer,backend.getLocalPort());
            }
            Class<?> endpoints=type(loader,"server.ServerEndpointResolver$ResolvedEndpoints");
            var endpointConstructor=endpoints.getDeclaredConstructors()[0]; endpointConstructor.setAccessible(true);
            Object resolved=endpointConstructor.newInstance("127.0.0.1","127.0.0.1",backend.getLocalPort(),zstdPort);
            Class<?> provider=type(loader,"server.DiscoveryDocumentProvider");
            Object documents=call(provider,"create",null,instance,resolved);
            Path executable=(Path)call(type(loader,"core.NativeWstunnel"),"resolve",null,instance);
            Path restrictions=instance.resolve("smoke-restrictions.yaml");
            Method restrict=controller.getDeclaredMethod("restrictions",String.class,String.class,List.class); restrict.setAccessible(true);
            Files.writeString(restrictions,(String)restrict.invoke(null,"miguelnetwork-v1","127.0.0.1",
                    zstdPort==null?List.of(backend.getLocalPort()):List.of(backend.getLocalPort(),zstdPort)));
            call(controller,"startStandalone",null,executable,restrictions,documents,resolved);
            InetSocketAddress local;
            if(zstdClient) {
                Class<?> address=Class.forName("net.minecraft.class_639",true,loader);
                Method parse=Arrays.stream(address.getDeclaredMethods()).filter(m->java.lang.reflect.Modifier.isStatic(m.getModifiers())
                        && m.getReturnType()==address && Arrays.equals(m.getParameterTypes(),new Class<?>[]{String.class})).findFirst().orElseThrow();
                Object original=parse.invoke(null,logicalHost+":"+publicPort);
                Class<?> hooks=Class.forName("cn.tohsaka.factory.zstdnet.coremod.ConnectScreenHooks",true,loader);
                Object data=null;
                if(compressed) {
                    Class<?> serverData=Class.forName("net.minecraft.class_642",true,loader);
                    var constructor=Arrays.stream(serverData.getConstructors()).filter(c->c.getParameterCount()==3).findFirst().orElseThrow();
                    Class<?> kind=constructor.getParameterTypes()[2];
                    data=constructor.newInstance("Smoke",logicalHost+":"+publicPort,kind==boolean.class?false:kind.getEnumConstants()[0]);
                    original=parse.invoke(null,"127.0.0.1:12345");
                    call(hooks,"setBypass",null,true);
                }
                Object redirected=call(hooks,"interceptConnect",null,original,data);
                proxy=call(hooks,"takeProxy",null);
                if(proxy==null)throw new AssertionError("Proxy was not handed back to ZstdNet");
                int port=(Integer)call(proxy.getClass(),"localPort",proxy);
                Method getPort=address.getMethod("method_2954");
                if(!getPort.invoke(redirected).equals(port))throw new AssertionError("Wrong returned local proxy");
                String mode=call(proxy.getClass(),"mode",proxy).toString();
                if(!mode.equals(compressed?"ZSTD":"RAW"))throw new AssertionError("Wrong proxy mode "+mode);
                local=new InetSocketAddress("127.0.0.1",port);
                if(!local.equals(call(manager,"redirect",null,local)))throw new AssertionError("Double tunnelling");
            } else local=(InetSocketAddress)call(manager,"redirect",null,InetSocketAddress.createUnresolved(logicalHost,publicPort));
            try(Socket socket=new Socket()) {
                socket.connect(local,10000); socket.setSoTimeout(20000);
                socket.getOutputStream().write(handshake); socket.getOutputStream().write(payload); socket.getOutputStream().flush();
                if(!Arrays.equals(payload,socket.getInputStream().readNBytes(payload.length)))throw new AssertionError("Downstream bytes changed");
            }
            received.get(20,TimeUnit.SECONDS);
            System.out.println("MIGUELNETWORK_TRANSFER_OK zstdClient="+zstdClient+" compressed="+compressed+" bytes="+payload.length);
        } finally {
            if(proxy!=null)call(proxy.getClass(),"close",proxy);
            call(manager,"stop",null); call(controller,"stop",null);
            if(zstdServer!=null) {
                // Let real connections finish before destroying the upstream runtime's guard/stats.
                var workersField=runtime.getDeclaredField("workers"); workersField.setAccessible(true);
                Object workers=workersField.get(zstdServer);
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
                while(workers instanceof ThreadPoolExecutor pool && pool.getActiveCount()>0 && System.nanoTime()<deadline) Thread.sleep(10);
                call(runtime,"stop",zstdServer);
            }
            executor.shutdownNow();
        }
    }
}
