package cn.tx.lock;

import org.I0Itec.zkclient.IZkDataListener;
import org.I0Itec.zkclient.ZkClient;

import java.util.List;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

public class DistributeLock {


    private ZkClient zkClient;

    /**
     * 分布式锁的创建
     */
    public DistributeLock(){
        //创建了zk的连接
        zkClient = new ZkClient("192.168.0.108", 6000, 2000);
        //创建lock节点
        boolean exists = zkClient.exists("/lock");
        if(!exists){
            zkClient.createPersistent("/lock");
        }
    }

    /**
     * 封装节点
     */
    class Node{

        private String nodeNath;


        public Node(String nodeNath) {
            this.nodeNath = nodeNath;
        }

        public String getNodeNath() {
            return nodeNath;
        }

        public void setNodeNath(String nodeNath) {
            this.nodeNath = nodeNath;
        }
    }

    /**
     * 创建临时节点
     * @return
     */
    public Node createNode(){
        //创建临时节点
        String path = zkClient.createEphemeralSequential("/lock/tx_node", "N");
        Node node = new Node(path);
        return node;
    }

    /**
     * 加锁
     */
    public Node lock(){
        //创建临时节点
        Node node = createNode();
        //如果没能获得到锁，当前这个线程就要等待挂起
        if(!tryAcqire(node)){
            synchronized (node){
                try {
                    node.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        //如果获得到锁了，就返回
        return node;
    }

    /**
     * 解锁
     */
    public void unlock(Node node){
        zkClient.delete(node.getNodeNath());
    }

    /**
     * 尝试获得锁
     * @return
     */
    public boolean tryAcqire(Node node){
        boolean isLock = false;
        //获得持久节点下的所有的孩子节点
        List<String> list = zkClient.getChildren("/lock")
                .stream()
                .sorted()
                .map(n -> "/lock/" + n)
                .collect(Collectors.toList());
        //获得第一个元素就是最小的节点
        String firstPath = list.get(0);
        if (firstPath.equals(node.getNodeNath())) {
            isLock = true;
        }else{
            //获得前一个顺序节点
            String prePath = list.get(list.indexOf(node.getNodeNath()) - 1);
            //给前一个节点创建监听器
            zkClient.subscribeDataChanges(prePath, new IZkDataListener() {
                @Override
                public void handleDataChange(String dataPath, Object data) throws Exception {

                }

                @Override
                public void handleDataDeleted(String dataPath) throws Exception {
                    System.out.println(dataPath+" 被删除");
                    //唤醒当前的节点
                    synchronized (node){
                        node.notify();
                    }
                    //删除事件
                    zkClient.unsubscribeDataChanges(prePath, this);
                }
            });
        }
        return isLock;
    }

}
