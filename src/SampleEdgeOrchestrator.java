/*
 * Title:        EdgeCloudSim - Edge Orchestrator
 *
 * Description:
 * SampleEdgeOrchestrator offloads tasks to proper server
 * based on the applied scenario.
 *
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 * Copyright (c) 2022, Bogazici University, Istanbul, Turkey
 */

package edu.boun.edgecloudsim.applications.tutorial3;

import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;

import edu.boun.edgecloudsim.cloud_server.CloudVM;
import edu.boun.edgecloudsim.core.SimManager;
import edu.boun.edgecloudsim.core.SimSettings;
import edu.boun.edgecloudsim.edge_orchestrator.EdgeOrchestrator;
import edu.boun.edgecloudsim.edge_server.EdgeVM;
import edu.boun.edgecloudsim.edge_client.CpuUtilizationModel_Custom;
import edu.boun.edgecloudsim.edge_client.Task;
import edu.boun.edgecloudsim.utils.SimLogger;
import edu.boun.edgecloudsim.utils.SimUtils;

/**
 * Sample Edge Orchestrator.
 *
 * Policies:
 * NETWORK_BASED: select Cloud when estimated WAN bandwidth exceeds 5 Mbps.
 * UTILIZATION_BASED: select Cloud when average Edge utilization exceeds 75.
 * RANDOM: select Cloud or Edge with equal probability.
 * HYBRID_BASED: compare estimated network delay and CPU-load penalties,
 * while checking whether a VM has enough residual CPU capacity.
 *
 * VM selection chooses the VM with the largest residual CPU capacity
 * that can accommodate the task's predicted CPU utilization.
 */
public class SampleEdgeOrchestrator extends EdgeOrchestrator {

    private int numberOfHost;

    public SampleEdgeOrchestrator(String _policy, String _simScenario) {
        super(_policy, _simScenario);
    }

    @Override
    public void initialize() {
        numberOfHost = SimSettings.getInstance().getNumOfEdgeHosts();
    }

    /**
     * Calculate average CPU utilization across all Cloud VMs.
     */
    private double getAverageCloudUtilization() {
        double totalUtilization = 0.0;
        int vmCounter = 0;

        List<Host> hosts = SimManager.getInstance()
                .getCloudServerManager()
                .getDatacenter()
                .getHostList();

        for (int hostIndex = 0; hostIndex < hosts.size(); hostIndex++) {
            List<CloudVM> vmArray = SimManager.getInstance()
                    .getCloudServerManager()
                    .getVmList(hostIndex);

            for (CloudVM vm : vmArray) {
                totalUtilization += vm.getCloudletScheduler()
                        .getTotalUtilizationOfCpu(CloudSim.clock());
                vmCounter++;
            }
        }

        return vmCounter == 0 ? 0.0 : totalUtilization / vmCounter;
    }

    /**
     * Estimate a nonlinear CPU-load penalty in seconds.
     * The penalty increases sharply as utilization approaches 100%.
     * The coefficient is an experimental value that should be tuned
     * using repeated simulation runs.
     */
    private double calculateLoadPenalty(double utilization) {
        double safeUtilization = Math.max(0.0, Math.min(utilization, 99.0));
        return 0.25 * safeUtilization / (100.0 - safeUtilization);
    }

    /**
     * Check whether at least one VM at the selected destination has
     * enough residual CPU capacity for this task.
     */
    private boolean hasAvailableVm(Task task, int deviceId) {
        CpuUtilizationModel_Custom cpuModel =
                (CpuUtilizationModel_Custom) task.getUtilizationModelCpu();

        if (deviceId == SimSettings.CLOUD_DATACENTER_ID) {
            List<Host> hosts = SimManager.getInstance()
                    .getCloudServerManager()
                    .getDatacenter()
                    .getHostList();

            for (int hostIndex = 0; hostIndex < hosts.size(); hostIndex++) {
                List<CloudVM> vmArray = SimManager.getInstance()
                        .getCloudServerManager()
                        .getVmList(hostIndex);

                for (CloudVM vm : vmArray) {
                    double requiredCapacity =
                            cpuModel.predictUtilization(vm.getVmType());
                    double freeCapacity = 100.0
                            - vm.getCloudletScheduler()
                            .getTotalUtilizationOfCpu(CloudSim.clock());

                    if (requiredCapacity <= freeCapacity) {
                        return true;
                    }
                }
            }
        } else if (deviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
            for (int hostIndex = 0; hostIndex < numberOfHost; hostIndex++) {
                List<EdgeVM> vmArray = SimManager.getInstance()
                        .getEdgeServerManager()
                        .getVmList(hostIndex);

                for (EdgeVM vm : vmArray) {
                    double requiredCapacity =
                            cpuModel.predictUtilization(vm.getVmType());
                    double freeCapacity = 100.0
                            - vm.getCloudletScheduler()
                            .getTotalUtilizationOfCpu(CloudSim.clock());

                    if (requiredCapacity <= freeCapacity) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Override
    public int getDeviceToOffload(Task task) {
        int result;

        // A small dummy task is used only by NETWORK_BASED to estimate WAN delay.
        Task dummyTask = new Task(
                0,
                0,
                0,
                0,
                128,
                128,
                new UtilizationModelFull(),
                new UtilizationModelFull(),
                new UtilizationModelFull()
        );

        double wanDelay = SimManager.getInstance()
                .getNetworkModel()
                .getUploadDelay(
                        task.getMobileDeviceId(),
                        SimSettings.CLOUD_DATACENTER_ID,
                        dummyTask
                );

        double wanBW = (wanDelay == 0) ? 0 : (1 / wanDelay);

        // Average CPU utilization across Edge VMs.
        double edgeUtilization = SimManager.getInstance()
                .getEdgeServerManager()
                .getAvgUtilization();

        // --------------------------------------------------
        // Policy 1: NETWORK_BASED (existing behavior preserved)
        // --------------------------------------------------
        if (policy.equals("NETWORK_BASED")) {
            if (wanBW > 5) {
                result = SimSettings.CLOUD_DATACENTER_ID;
            } else {
                result = SimSettings.GENERIC_EDGE_DEVICE_ID;
            }
        }

        // --------------------------------------------------
        // Policy 2: UTILIZATION_BASED (existing behavior preserved)
        // --------------------------------------------------
        else if (policy.equals("UTILIZATION_BASED")) {
            if (edgeUtilization > 75) {
                result = SimSettings.CLOUD_DATACENTER_ID;
            } else {
                result = SimSettings.GENERIC_EDGE_DEVICE_ID;
            }
        }

        // --------------------------------------------------
        // Policy 3: HYBRID_BASED
        // --------------------------------------------------
        else if (policy.equals("HYBRID_BASED")) {
            double cloudUtilization = getAverageCloudUtilization();

            // Estimate upload and download delays for Edge.
            double edgeUploadDelay = SimManager.getInstance()
                    .getNetworkModel()
                    .getUploadDelay(
                            task.getMobileDeviceId(),
                            SimSettings.GENERIC_EDGE_DEVICE_ID,
                            task
                    );

            double edgeDownloadDelay = SimManager.getInstance()
                    .getNetworkModel()
                    .getDownloadDelay(
                            SimSettings.GENERIC_EDGE_DEVICE_ID,
                            task.getMobileDeviceId(),
                            task
                    );

            // Estimate upload and download delays for Cloud.
            double cloudUploadDelay = SimManager.getInstance()
                    .getNetworkModel()
                    .getUploadDelay(
                            task.getMobileDeviceId(),
                            SimSettings.CLOUD_DATACENTER_ID,
                            task
                    );

            double cloudDownloadDelay = SimManager.getInstance()
                    .getNetworkModel()
                    .getDownloadDelay(
                            SimSettings.CLOUD_DATACENTER_ID,
                            task.getMobileDeviceId(),
                            task
                    );

            boolean edgeDelayValid =
                    Double.isFinite(edgeUploadDelay) && edgeUploadDelay >= 0.0
                    && Double.isFinite(edgeDownloadDelay) && edgeDownloadDelay >= 0.0;

            boolean cloudDelayValid =
                    Double.isFinite(cloudUploadDelay) && cloudUploadDelay >= 0.0
                    && Double.isFinite(cloudDownloadDelay) && cloudDownloadDelay >= 0.0;

            double edgeTotalDelay = edgeUploadDelay + edgeDownloadDelay;
            double cloudTotalDelay = cloudUploadDelay + cloudDownloadDelay;

            // Check destination CPU capacity before comparing the scores.
            boolean edgeHasCapacity = hasAvailableVm(
                    task, SimSettings.GENERIC_EDGE_DEVICE_ID);
            boolean cloudHasCapacity = hasAvailableVm(
                    task, SimSettings.CLOUD_DATACENTER_ID);

            // If only one destination has a VM with enough free CPU, use it.
            if (edgeHasCapacity && !cloudHasCapacity) {
                result = SimSettings.GENERIC_EDGE_DEVICE_ID;
            } else if (!edgeHasCapacity && cloudHasCapacity) {
                result = SimSettings.CLOUD_DATACENTER_ID;
            } else if (edgeDelayValid && !cloudDelayValid) {
                result = SimSettings.GENERIC_EDGE_DEVICE_ID;
            } else if (!edgeDelayValid && cloudDelayValid) {
                result = SimSettings.CLOUD_DATACENTER_ID;
            } else if (edgeDelayValid && cloudDelayValid) {
                // A lower score is preferred: network delay + CPU-load penalty.
                double edgeScore = edgeTotalDelay
                        + calculateLoadPenalty(edgeUtilization);
                double cloudScore = cloudTotalDelay
                        + calculateLoadPenalty(cloudUtilization);

                if (cloudScore < edgeScore) {
                    result = SimSettings.CLOUD_DATACENTER_ID;
                } else {
                    result = SimSettings.GENERIC_EDGE_DEVICE_ID;
                }
            } else {
                // If neither path can be evaluated, fall back to Edge.
                result = SimSettings.GENERIC_EDGE_DEVICE_ID;
            }
        }

        // --------------------------------------------------
        // Policy 4: RANDOM (existing behavior preserved)
        // --------------------------------------------------
        else if (policy.equals("RANDOM")) {
            double randomNumber = SimUtils.getRandomDoubleNumber(0, 1);
            if (randomNumber < 0.5) {
                result = SimSettings.CLOUD_DATACENTER_ID;
            } else {
                result = SimSettings.GENERIC_EDGE_DEVICE_ID;
            }
        } else {
            SimLogger.printLine(
                    "Unknown edge orchestrator policy! Terminating simulation..."
            );
            System.exit(0);
            return SimSettings.GENERIC_EDGE_DEVICE_ID;
        }

        return result;
    }

    @Override
    public Vm getVmToOffload(Task task, int deviceId) {
        Vm selectedVM = null;

        // --------------------------------------------------
        // Cloud VM selection
        // --------------------------------------------------
        if (deviceId == SimSettings.CLOUD_DATACENTER_ID) {
            double selectedVmCapacity = 0;

            List<Host> list = SimManager.getInstance()
                    .getCloudServerManager()
                    .getDatacenter()
                    .getHostList();

            for (int hostIndex = 0; hostIndex < list.size(); hostIndex++) {
                List<CloudVM> vmArray = SimManager.getInstance()
                        .getCloudServerManager()
                        .getVmList(hostIndex);

                for (int vmIndex = 0; vmIndex < vmArray.size(); vmIndex++) {
                    double requiredCapacity =
                            ((CpuUtilizationModel_Custom) task.getUtilizationModelCpu())
                                    .predictUtilization(vmArray.get(vmIndex).getVmType());

                    double targetVmCapacity = 100.0
                            - vmArray.get(vmIndex).getCloudletScheduler()
                            .getTotalUtilizationOfCpu(CloudSim.clock());

                    if (requiredCapacity <= targetVmCapacity
                            && targetVmCapacity > selectedVmCapacity) {
                        selectedVM = vmArray.get(vmIndex);
                        selectedVmCapacity = targetVmCapacity;
                    }
                }
            }
        }

        // --------------------------------------------------
        // Edge VM selection
        // --------------------------------------------------
        else if (deviceId == SimSettings.GENERIC_EDGE_DEVICE_ID) {
            double selectedVmCapacity = 0;

            for (int hostIndex = 0; hostIndex < numberOfHost; hostIndex++) {
                List<EdgeVM> vmArray = SimManager.getInstance()
                        .getEdgeServerManager()
                        .getVmList(hostIndex);

                for (int vmIndex = 0; vmIndex < vmArray.size(); vmIndex++) {
                    double requiredCapacity =
                            ((CpuUtilizationModel_Custom) task.getUtilizationModelCpu())
                                    .predictUtilization(vmArray.get(vmIndex).getVmType());

                    double targetVmCapacity = 100.0
                            - vmArray.get(vmIndex).getCloudletScheduler()
                            .getTotalUtilizationOfCpu(CloudSim.clock());

                    if (requiredCapacity <= targetVmCapacity
                            && targetVmCapacity > selectedVmCapacity) {
                        selectedVM = vmArray.get(vmIndex);
                        selectedVmCapacity = targetVmCapacity;
                    }
                }
            }
        } else {
            SimLogger.printLine(
                    "Unknown device id! The simulation has been terminated."
            );
            System.exit(0);
        }

        return selectedVM;
    }

    @Override
    public void processEvent(SimEvent arg0) {
        // No asynchronous internal events are required.
    }

    @Override
    public void shutdownEntity() {
        // No resources to release.
    }

    @Override
    public void startEntity() {
        // No startup scheduling is required.
    }
}
