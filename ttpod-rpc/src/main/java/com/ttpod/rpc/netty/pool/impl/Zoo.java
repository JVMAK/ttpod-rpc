package com.ttpod.rpc.netty.pool.impl;

import org.apache.zookeeper.*;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * date: 14-2-13 下午4:18
 *
 * @author: yangyang.cong@ttpod.com
 */
public  abstract class Zoo {


    public static ZooKeeper connect(String zkAddress){
        try {
            final CountDownLatch connectedSignal = new CountDownLatch(1);
            ZooKeeper zooKeeper = new ZooKeeper(zkAddress,3000,new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if (event.getState() == Event.KeeperState.SyncConnected) {
                        connectedSignal.countDown();
                    }
                }
            });
            connectedSignal.await();
            return zooKeeper;
//            zooKeeper.getChildren();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            throw new RuntimeException("cann't conn to : "+ zkAddress,e);
        }
    }


    public static String flipPath(String groupName){
        groupName = groupName.trim().replaceAll("/+", UNIX_FILE_SEPARATOR);
        if(groupName.length() <= 1){
            throw new RuntimeException("path name is too short :" + groupName);
        }
        if(!groupName.startsWith(UNIX_FILE_SEPARATOR)){
            groupName = UNIX_FILE_SEPARATOR+ groupName;
        }
        if(groupName.endsWith(UNIX_FILE_SEPARATOR)){
            groupName = groupName.substring(0,groupName.length()-1);
        }
        return groupName;
    }


    public static final String UNIX_FILE_SEPARATOR = "/";

    public static boolean syncCreate(final String flipPath,ZooKeeper zooKeeper){
        if(! flipPath.startsWith(UNIX_FILE_SEPARATOR)){
            throw new RuntimeException("flipPath must starts with / :" + flipPath);
        }
        final AtomicBoolean success = new AtomicBoolean(false);
        final CountDownLatch latch = new CountDownLatch(1);
        AsyncCallback.StringCallback cb = new AsyncCallback.StringCallback() {
            @Override
            public void processResult(int rc, String path, Object ctx, String name) {
                if(flipPath.equals(path)) {//wait for the last path
                    success.set((rc == KeeperException.Code.NODEEXISTS.intValue()) ||
                            (rc == KeeperException.Code.OK.intValue()));
                    latch.countDown();
                }
            }
        };
        String[] pathParts = flipPath.substring(1).split(UNIX_FILE_SEPARATOR);
        StringBuilder path = new StringBuilder();
        for (String pathElement : pathParts) {
            path.append(UNIX_FILE_SEPARATOR).append(pathElement);
            //send requests to create all parts of the path without waiting for the
            //results of previous calls to return
            zooKeeper.create(path.toString(), null,
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, cb,null);
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return success.get();
    }
}
