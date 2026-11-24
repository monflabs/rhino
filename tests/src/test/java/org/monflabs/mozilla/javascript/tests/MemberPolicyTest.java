package org.monflabs.mozilla.javascript.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import org.junit.jupiter.api.Test;
import org.monflabs.mozilla.javascript.ClassCache;
import org.monflabs.mozilla.javascript.Context;
import org.monflabs.mozilla.javascript.ContextFactory;
import org.monflabs.mozilla.javascript.MemberPolicy;
import org.monflabs.mozilla.javascript.ScriptableObject;
import org.monflabs.mozilla.javascript.testutils.Utils;

public class MemberPolicyTest {

    public static class Host {
        public String visible() {
            return "visible";
        }

        public String secret() {
            return "secret";
        }

        public String oldName() {
            return "renamed";
        }

        public String pick(String s) {
            return "string";
        }

        public String pick(Integer i) {
            return "integer";
        }
    }

    public static class Hidden {
        public String hello() {
            return "hello";
        }
    }

    private static final class Policy implements MemberPolicy {
        @Override
        public boolean isClassVisible(Class<?> cls) {
            return cls != Hidden.class;
        }

        @Override
        public String publicName(Class<?> cls, Member member) {
            return switch (member.getName()) {
                case "secret" -> null;
                case "oldName" -> "newName";
                default -> member.getName();
            };
        }

        @Override
        public boolean isParameterNonNull(Executable method, int index) {
            return method.getName().equals("pick") && method.getParameterTypes()[0] == String.class;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Policy;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }

    private static ContextFactory factory(boolean nonNull) {
        return new ContextFactory() {
            @Override
            protected boolean hasFeature(Context cx, int featureIndex) {
                if (featureIndex == Context.FEATURE_NONNULL_PARAMETERS) {
                    return nonNull;
                }
                return super.hasFeature(cx, featureIndex);
            }
        };
    }

    private static Object eval(ContextFactory factory, MemberPolicy policy, String script) {
        Object[] result = new Object[1];
        Utils.runWithAllModes(
                factory,
                cx -> {
                    var scope = cx.initStandardObjects();
                    ClassCache.get(scope).setMemberPolicy(policy);
                    ScriptableObject.putProperty(
                            scope, "host", Context.javaToJS(new Host(), scope));
                    ScriptableObject.putProperty(
                            scope, "hidden", Context.javaToJS(new Hidden(), scope));
                    result[0] = cx.evaluateString(scope, script, "t", 1, null);
                    return null;
                });
        return result[0];
    }

    @Test
    void membersCanBeHiddenAndRenamed() {
        assertEquals(
                "visible|renamed|undefined|undefined|undefined",
                eval(
                        factory(false),
                        new Policy(),
                        "[host.visible(), host.newName(), typeof host.secret, typeof host.oldName,"
                                + " typeof hidden.hello].join('|')"));
    }

    @Test
    void withoutAPolicyEverythingIsVisible() {
        assertEquals(
                "secret|hello",
                eval(factory(false), null, "[host.secret(), hidden.hello()].join('|')"));
    }

    @Test
    void nonNullParametersRuleOutOverloads() {
        assertEquals("integer", eval(factory(true), new Policy(), "String(host.pick(null))"));
    }

    @Test
    void snapshotRestoresUnderTheSamePolicyOnly() {
        var cx = Context.enter();
        try {
            var a = cx.initStandardObjects();
            var policy = new Policy();
            ClassCache.get(a).setMemberPolicy(policy);
            ScriptableObject.putProperty(a, "host", Context.javaToJS(new Host(), a));
            cx.evaluateString(a, "host.visible()", "t", 1, null);
            var snapshot = ClassCache.get(a).snapshot();
            assertTrue(snapshot.size() > 0);

            var b = cx.initStandardObjects();
            assertThrows(IllegalStateException.class, () -> snapshot.restore(ClassCache.get(b)));
            ClassCache.get(b).setMemberPolicy(new Policy());
            snapshot.restore(ClassCache.get(b));
            ScriptableObject.putProperty(b, "host", Context.javaToJS(new Host(), b));
            assertEquals(
                    "visible|undefined",
                    cx.evaluateString(
                            b, "host.visible() + '|' + typeof host.secret", "t", 1, null));
        } finally {
            Context.exit();
        }
    }
}
