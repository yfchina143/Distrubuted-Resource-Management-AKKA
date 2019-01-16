package cmsc433.p4.studentFile;

//package util;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

import akka.actor.ActorRef;

public class StudentLock {

    private ConcurrentHashMap<String, HashMap<String, Integer>> users = new ConcurrentHashMap<String, HashMap<String, Integer>>();


    //Resource methods
    public boolean addResource(String resource) {
        if (hasResource(resource)) {
            return false;
        }
        users.put(resource, new HashMap<String, Integer>());
        return true;


    }

    public boolean removeResource(String resource) {
        if (hasResource(resource)) {
            users.remove(resource);

            return true;
        } else {
            return false;
        }
    }


    //User methods
    public boolean addUser( String resource,ActorRef user) {
        if (hasResource(resource)) {
            HashMap<String, Integer> Locks = users.get(resource);

            if (!Locks.containsKey(user.toString())) {
                Locks.put(user.toString(), 1);
            } else {
                int numLocks = Locks.get(user.toString());
                Locks.replace(user.toString(), numLocks++);

            }

            return true;
        } else {
            return false;
        }
    }

    public boolean removeUser(String resource,ActorRef user) {
        if (!hasResource(resource)) {
           return false;
        } else {
            HashMap<String, Integer> userLocks = users.get(resource);

            if (userLocks.containsKey(user.toString())) {
                int Locks = userLocks.get(user.toString());
                userLocks.replace(user.toString(), Locks--);

                if (Locks == 0) {
                    userLocks.remove(user.toString());
                }

                return true;
            } else {
                return false;
            }
        }
    }


    //Util methods
    public boolean hasUsers(String resource) {
        if (hasResource(resource)) {
            return !users.get(resource).isEmpty();
        } else {
            return false;
        }
    }

    public boolean hasUser(String resource,ActorRef user) {
        if (!hasResource(resource)) {
            return false;

        } else {
            return users.get(resource).containsKey(user.toString());
        }
    }

    public boolean isOnlyUser( String resource,ActorRef user) {
        if (!hasResource(resource)) {
          return false;
        } else {
            HashMap<String, Integer> userLocks = users.get(resource);

            if (userLocks.size() != 1) {
                return false;
            } else {
                return userLocks.containsKey(user.toString());
            }
        }
    }

    private boolean hasResource(String resourceName) {
        return users.containsKey(resourceName);
    }
}