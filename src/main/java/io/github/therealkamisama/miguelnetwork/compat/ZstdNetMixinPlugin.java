package io.github.therealkamisama.miguelnetwork.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

/** Checks optional API bytecode without loading the class before Mixin transforms it. */
public final class ZstdNetMixinPlugin implements IMixinConfigPlugin {
    private static final String ROOT = "cn/tohsaka/factory/zstdnet/";
    private static volatile boolean applied;

    public static boolean isApplied() { return applied; }
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public List<String> getMixins() { return null; }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) {}
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (mixin.endsWith(".ZstdNetConnectScreenHooksMixin")) applied = true;
    }

    @Override
    public boolean shouldApplyMixin(String target, String mixin) {
        if (!mixin.endsWith(".ZstdNetConnectScreenHooksMixin")) return true;
        var mod = FabricLoader.getInstance().getModContainer("zstdnet");
        if (mod.isEmpty()) return false;
        String version = mod.get().getMetadata().getVersion().getFriendlyString();
        boolean valid = ZstdNetServerCompatibility.isSupportedVersion(version);
        try {
            valid = valid && supportsApi(mod.get());
        } catch (IOException | RuntimeException exception) {
            valid = false;
        }
        if (!valid) LoggerFactory.getLogger("miguelnetwork").warn(
                "MiguelNetwork ZstdNet client adapter disabled for unsupported version or API {}", version);
        return valid;
    }

    private static boolean supportsApi(ModContainer mod) throws IOException {
        ClassNode hooks = read(mod, "coremod/ConnectScreenHooks");
        // Published Fabric JARs use intermediary; Loom may remap a local development dependency to named.
        String address = mapped("net.minecraft.class_639");
        String data = mapped("net.minecraft.class_642");
        boolean intercept = method(hooks, "interceptConnect", "(Lnet/minecraft/class_639;Lnet/minecraft/class_642;)Lnet/minecraft/class_639;", true)
                || method(hooks, "interceptConnect", "(L" + address + ";L" + data + ";)L" + address + ";", true);
        String handle = "L" + ROOT + "proxy/LocalZstdNet$ProxyHandle;";
        String mode = "L" + ROOT + "proxy/LocalZstdNet$Mode;";
        return intercept && field(hooks, "bypassing", "Z") && field(hooks, "LOCK", "Ljava/lang/Object;")
                && field(hooks, "currentProxy", handle)
                && method(hooks, "takeProxy", "()" + handle, true)
                && method(read(mod, "proxy/LocalZstdNet"), "start", "(Ljava/lang/String;ILjava/lang/String;ILjava/lang/String;II" + mode + ")" + handle, true)
                && method(read(mod, "ClientConfig"), "getLevel", "()I", true)
                && method(read(mod, "proxy/LocalZstdNet$ProxyHandle"), "localPort", "()I", false)
                && method(read(mod, "proxy/LocalZstdNet$ProxyHandle"), "close", "()V", false)
                && field(read(mod, "proxy/LocalZstdNet$Mode"), "ZSTD", mode)
                && field(read(mod, "proxy/LocalZstdNet$Mode"), "RAW", mode);
    }

    private static String mapped(String intermediary) {
        return FabricLoader.getInstance().getMappingResolver().mapClassName("intermediary", intermediary).replace('.', '/');
    }
    private static ClassNode read(ModContainer mod, String name) throws IOException {
        var path = mod.findPath(ROOT + name + ".class").orElseThrow(() -> new IOException("Missing ZstdNet class " + name));
        ClassNode node = new ClassNode();
        new ClassReader(Files.readAllBytes(path)).accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }
    static boolean method(ClassNode node, String name, String descriptor, boolean isStatic) {
        return node.methods.stream().anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor)
                && (method.access & Opcodes.ACC_PUBLIC) != 0 && ((method.access & Opcodes.ACC_STATIC) != 0) == isStatic);
    }
    static boolean field(ClassNode node, String name, String descriptor) {
        return node.fields.stream().anyMatch(field -> field.name.equals(name) && field.desc.equals(descriptor)
                && (field.access & Opcodes.ACC_STATIC) != 0);
    }
}
