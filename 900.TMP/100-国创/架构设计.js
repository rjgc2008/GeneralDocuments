graph TB
    %% --- 全局样式定义 ---
    %% 配色方案：专业蓝（控制）、科技绿（网络）、活力橙（算力）
    classDef ctrlFill fill:#e3f2fd,stroke:#1565c0,stroke-width:2px,rx:10,ry:10,color:#0d47a1;
    classDef netFill fill:#e0f2f1,stroke:#00695c,stroke-width:2px,rx:10,ry:10,color:#004d40;
    classDef clusterFill fill:#fff3e0,stroke:#ef6c00,stroke-width:2px,rx:10,ry:10,color:#e65100;
    classDef nodeStyle fill:#ffffff,stroke:#cfd8dc,stroke-width:1px,rx:5,ry:5,color:#37474f;
    
    %% 线条样式
    linkStyle default stroke:#b0bec5,stroke-width:1px;
    
    %% --- 顶层控制系统 ---
    subgraph CTRL [SNDS 智能控制平面]
        direction TB
        POL([<i class="fa fa-chess-board"></i> 策略编排<br/>Policy Engine]):::nodeStyle
        INTMON([<i class="fa fa-chart-line"></i> INT 监测与分析]):::nodeStyle
        SEC([<i class="fa fa-shield-alt"></i> 内生安全治理]):::nodeStyle
    end
    
    %% --- 跨域骨干 ---
    subgraph BACKBONE [跨集群高速确定性骨干网]
        direction TB
        OCS[<i class="fa fa-random"></i> 光路交换 OCS]:::nodeStyle
        EPS[<i class="fa fa-network-wired"></i> 电交换 EPS]:::nodeStyle
        DIPNET[[<i class="fa fa-road"></i> DIP/DetNet 确定性传输层]]:::nodeStyle
    end

    %% --- 分布式算力集群层 ---
    subgraph CLUSTERS [分布式算力集群层]
        direction LR
        
        subgraph A [核心集群 A <br/>量子/AI]
            direction TB
            QA[<i class="fa fa-atom"></i> 量子/AI 加速器]:::nodeStyle
            SNICA[<i class="fa fa-microchip"></i> SmartNIC / DPU]:::nodeStyle
        end

        subgraph B [核心集群 B <br/>超算中心]
            direction TB
            HPC[<i class="fa fa-server"></i> CPU/GPU 节点]:::nodeStyle
            SNICB[<i class="fa fa-microchip"></i> SmartNIC / DPU]:::nodeStyle
        end

        subgraph C [边缘集群 C]
            direction TB
            EDGE[<i class="fa fa-satellite-dish"></i> 边缘采集/预处理]:::nodeStyle
            SNICC[<i class="fa fa-microchip"></i> 轻量化 SmartNIC]:::nodeStyle
        end
    end

    %% --- 拓扑连接 ---
    
    %% 控制平面下发 (使用虚线表示控制流)
    CTRL -.-|策略下发 / 时隙配置| BACKBONE
    CTRL -.-|资源编排 / QoS| CLUSTERS

    %% 跨域数据流连接 (使用加粗实线表示数据流)
    SNICA ====>|确定性接入| BACKBONE
    SNICB ====>|确定性接入| BACKBONE
    SNICC ==>|边缘接入| BACKBONE

    %% 骨干内部路径
    OCS --> DIPNET
    EPS --> DIPNET

    %% --- 应用子图样式 ---
    class CTRL ctrlFill
    class BACKBONE netFill
    class CLUSTERS clusterFill
    class A,B,C nodeStyle