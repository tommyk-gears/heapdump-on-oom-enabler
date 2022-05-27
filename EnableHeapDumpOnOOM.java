import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class EnableHeapDumpOnOOM {

    public static void main(String[] args) throws IOException,
            MalformedObjectNameException,
            ReflectionException,
            InstanceNotFoundException,
            MBeanException,
            AttachNotSupportedException {

        if (args.length != 1) {
            System.err.println("Usage: java EnableHeapDumpOnOOM.java <pid>");
            System.exit(1);
        }

        VirtualMachineDescriptor vmDescriptor = findVMDescriptor(args[0]);

        System.out.println("Trying to enable HeapDumpOnOutOfMemoryError on " + vmDescriptor.displayName());

        VirtualMachine vm = null;
        try {
            vm = VirtualMachine.attach(vmDescriptor);

            String connectorAddress = vm.getAgentProperties().getProperty("com.sun.management.jmxremote.localConnectorAddress");
            if (connectorAddress == null) {
                System.out.println("No local connector address, will start local agent");
                connectorAddress = vm.startLocalManagementAgent();
            }

            JMXServiceURL url = new JMXServiceURL(connectorAddress);
            try (JMXConnector jmxConnector = JMXConnectorFactory.connect(url)) {
                MBeanServerConnection mbeanConn = jmxConnector.getMBeanServerConnection();
                setVMOption(mbeanConn, "HeapDumpOnOutOfMemoryError", "true");
            }
        } finally {
            if (vm != null) {
                vm.detach();
            }
        }
    }

    private static VirtualMachineDescriptor findVMDescriptor(String pid) {
        List<VirtualMachineDescriptor> vms = VirtualMachine.list();

        return vms.stream().filter(d -> d.id().equals(pid)).findFirst()
                .orElseGet(() -> {
                    System.err.println("No JVM with pid <" + pid + "> found. Found these JVMs;");
                    vms.forEach(desc -> System.err.println(desc.id() + " " + desc.displayName()));
                    System.exit(1);
                    return null;
                });
    }

    private static void setVMOption(MBeanServerConnection mBeanConn, String vmOption, String value) throws
            MalformedObjectNameException,
            ReflectionException,
            InstanceNotFoundException,
            MBeanException,
            IOException {

        ObjectName diagnosticsMbeanName = new ObjectName("com.sun.management:type=HotSpotDiagnostic");

        CompositeData compositeData = (CompositeData) mBeanConn.invoke(diagnosticsMbeanName,
                "getVMOption",
                new Object[]{vmOption},
                new String[]{String.class.getName()});

        if (compositeData.containsKey("value") && Objects.equals(compositeData.get("value"), value)) {
            System.out.println(vmOption + " already has value '" + value + "'. Aborting.");
            System.exit(0);
        }
        if (compositeData.containsKey("writeable") && !Boolean.parseBoolean(compositeData.get("writeable").toString())) {
            System.err.println(vmOption + " is not writeable. Aborting.");
            System.exit(1);
        }

        mBeanConn.invoke(diagnosticsMbeanName,
                "setVMOption",
                new Object[]{vmOption, value},
                new String[]{String.class.getName(), String.class.getName()});
        System.out.println("Successfully updated " + vmOption);
    }
}
