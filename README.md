# Performance Evaluation of Dynamic Task Offloading Policies in Edge Computing Networks

**Student:** Abdalrheem Hasan Mohammad Salah  
**Instructor:** Dr. Jehad Hamamreh  
**Institution:** Computer Science Department, An-Najah National University  
**Course:** Wireless Networks Projects  

## 🔗 Base Framework & Acknowledgments
This project is an extension of the original **[EdgeCloudSim](https://github.com/CagataySonmez/EdgeCloudSim)** simulator developed by Cagatay Sonmez, Atay Ozgovde, and Cem Ersoy. While the core simulation engine, mobility models, and network modules are derived from the original repository, we customized and extended the `SampleEdgeOrchestrator` to design, implement, and evaluate our proposed `HYBRID_BASED` task offloading algorithm.

## 📌 Project Overview
This project presents a comprehensive performance evaluation of dynamic task offloading policies in a Multi-access Edge Computing (MEC) environment. The primary goal is to address the trade-off between Edge Virtual Machine (VM) capacity saturation and Wide Area Network (WAN) congestion under highly dense mobile network scenarios (1600 to 2000 devices).

## 🚀 Proposed Solution: HYBRID_BASED Orchestrator
Relying solely on edge server utilization or naive WAN delay thresholds leads to massive task failures (up to 38% under heavy loads) due to severe network degradation or VM capacity exhaustion. 

To overcome this, we designed and implemented a **Cross-Layer Hybrid Task Offloading Policy (`HYBRID_BASED`)** in `SampleEdgeOrchestrator.java`. Our algorithm features:
1. **Pre-Offloading VM Capacity Verification:** Uses `hasAvailableVm()` to prevent routing tasks to fully saturated servers.
2. **Dynamic Non-linear CPU-Load Penalty:** Calculates a penalty that rises steeply as utilization approaches 100%, allowing the orchestrator to weigh edge queuing pressure against WAN transmission delay.
3. **Delay Comparison Scoring:** Chooses the optimal execution tier (Edge vs. Cloud) based on a unified score combining network latency and the load penalty.

## 📊 Key Results
Extensive simulations (3 iterations per device density) demonstrated that our `HYBRID_BASED` policy significantly outperforms baseline approaches:
* **Eliminated VM Capacity Failures:** Reduced VM capacity failures by over **99.2%** at 2000 devices compared to the Network-based policy.
* **Minimized Network Delay:** Achieved a **63.8% to 85.3% reduction** in mean network delay across all tested scales.
* **High Reliability:** Maintained an overall task failure rate of approximately **2.86%** (median) even under extreme network congestion.

## 🛠️ How to Run the Simulation
1. Clone the repository and open it in your preferred Java IDE (Eclipse / VS Code).
2. Ensure you have Java JDK 17 or higher installed.
3. The configuration files are located in `scripts/tutorial3/config/`:
   * `default_config.properties` (Contains the orchestrator policies and device counts)
   * `applications.xml` (Defines AR, Health, and Infotainment app profiles)
   * `edge_devices.xml` (Defines the Edge and Cloud datacenter topologies)
4. Run `MainApp.java` located in `src/edu/boun/edgecloudsim/applications/tutorial3/`.
5. The simulation logs will be generated in the `sim_results/tutorial3/` directory.

## 📁 Repository Structure
* `/src`: Contains the modified EdgeCloudSim source code, including our custom `SampleEdgeOrchestrator.java`.
* `/config`: Contains the simulation setup files (`XML` and `.properties`).
* `/results`: Contains the compiled Excel statistical analysis and performance charts.
* `Report.pdf`: The final IEEE-formatted project report detailing the system architecture, mathematical models, and discussion.