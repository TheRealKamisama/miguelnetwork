package io.github.therealkamisama.miguelnetwork.compat;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

class ZstdNetMixinPluginTest {
    @Test void requiresExactPublicMethodSignatureAndStaticness() {
        ClassNode node = new ClassNode();
        node.methods.add(new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "start", "()I", null, null));
        assertTrue(ZstdNetMixinPlugin.method(node, "start", "()I", true));
        assertFalse(ZstdNetMixinPlugin.method(node, "start", "()J", true));
        assertFalse(ZstdNetMixinPlugin.method(node, "start", "()I", false));
        node.methods.get(0).access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
        assertFalse(ZstdNetMixinPlugin.method(node, "start", "()I", true));
    }

    @Test void rejectsMissingOrChangedHandoffFields() {
        ClassNode node = new ClassNode();
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, "bypassing", "Z", null, null));
        assertTrue(ZstdNetMixinPlugin.field(node, "bypassing", "Z"));
        assertFalse(ZstdNetMixinPlugin.field(node, "currentProxy", "Ljava/lang/Object;"));
        assertFalse(ZstdNetMixinPlugin.field(node, "bypassing", "I"));
        node.fields.get(0).access = Opcodes.ACC_PRIVATE;
        assertFalse(ZstdNetMixinPlugin.field(node, "bypassing", "Z"));
    }
}
