package cmsc433.p4.studentFile;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;



public class RequestList {
    ConcurrentHashMap<String, LinkedList<Object>> pendingRequests = new ConcurrentHashMap<String, LinkedList<Object>>();

    public boolean addResource(String resourceName) {
        if (!pendingRequests.containsKey(resourceName)) {
            LinkedList<Object> requests = new LinkedList<Object>();

            pendingRequests.put(resourceName, requests);

            return true;
        } else {
            return false;
        }
    }

    public boolean removeResource(String resourceName) {
        if (pendingRequests.containsKey(resourceName)) {
            pendingRequests.remove(resourceName);

            return true;
        } else {
            return false;
        }
    }

    public Object peekRequest(String resourceName) {
        if (pendingRequests.containsKey(resourceName)) {
            return pendingRequests.get(resourceName).getFirst();
        } else {
            return null;
        }
    }

    public Object getRequest(String resourceName) {
        if (pendingRequests.containsKey(resourceName)) {
            return pendingRequests.get(resourceName).removeFirst();
        } else {
            return null;
        }
    }
    public LinkedList<Object> getRequests(String resourceName) {
        return pendingRequests.getOrDefault(resourceName, new LinkedList<Object>());
    }

    public int size(String resourceName) {
        if (pendingRequests.containsKey(resourceName)) {
            return pendingRequests.get(resourceName).size();
        } else {
            return -1;
        }
    }

    public Object getLatestRequest(String resourceName) {
        return pendingRequests.get(resourceName).removeLast();
    }

    public boolean makeEarliestRequest(Object request, String resourceName) {
        if (pendingRequests.containsKey(resourceName)) {
            pendingRequests.get(resourceName).add(0, request);
            return true;
        } else {
            return false;
        }
    }

    public boolean addRequest(Object request, String resourceName) {
        if (pendingRequests.containsKey(resourceName)) {
            pendingRequests.get(resourceName).add(request);
            return true;
        } else {
            return false;
        }
    }

    public boolean replace(LinkedList<Object> requests, String resourceName) {
        if (pendingRequests.containsKey(resourceName)) {
            pendingRequests.get(resourceName).clear();
            pendingRequests.get(resourceName).addAll(requests);
            return true;
        } else {
            return false;
        }
    }
}