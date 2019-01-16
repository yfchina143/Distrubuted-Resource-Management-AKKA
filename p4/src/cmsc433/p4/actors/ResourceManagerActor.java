package cmsc433.p4.actors;
import java.util.*;

import cmsc433.p4.enums.*;
import cmsc433.p4.messages.*;
import cmsc433.p4.studentFile.RequestList;
import cmsc433.p4.studentFile.StudentLock;
import cmsc433.p4.util.*;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;

public class ResourceManagerActor extends UntypedActor {

	private ActorRef logger;					// Actor to send logging messages to

	/**
	 * Props structure-generator for this class.
	 * @return  Props structure
	 */
	static Props props (ActorRef logger) {
		return Props.create(ResourceManagerActor.class, logger);
	}

	/**
	 * Factory method for creating resource managers
	 * @param logger			Actor to send logging messages to
	 * @param system			Actor system in which manager will execute
	 * @return					Reference to new manager
	 */
	public static ActorRef makeResourceManager (ActorRef logger, ActorSystem system) {
		ActorRef newManager = system.actorOf(props(logger));
		return newManager;
	}

	/**
	 * Sends a message to the Logger Actor
	 * @param msg The message to be sent to the logger
	 */
	public void log (LogMsg msg) {
		logger.tell(msg, getSelf());
	}

	/**
	 * Constructor
	 *
	 * @param logger			Actor to send logging messages to
	 */
	private ResourceManagerActor(ActorRef logger) {
		super();
		this.logger = logger;
	}

	// You may want to add data structures for managing local resources and users, storing
	// remote managers, etc.
	//
	// REMEMBER:  YOU ARE NOT ALLOWED TO CREATE MUTABLE DATA STRUCTURES THAT ARE SHARED BY
	// MULTIPLE ACTORS!

	/* (non-Javadoc)
	 *
	 * You must provide an implementation of the onReceive() method below.
	 *
	 * @see akka.actor.UntypedActor#onReceive(java.lang.Object)
	 */


	//start of the 3 variable
	private HashMap<String, Resource> localResources = new HashMap<>();
	private HashSet<ActorRef> remoteManagers = new HashSet<>();
	private HashSet<ActorRef> localUsers = new HashSet<>();
	private HashMap<String, Set<ManagementRequestMsg>> disableMsg = new HashMap<>();
	private HashMap<String, Queue<AccessRequestMsg>> AcessReqestMsgQueue = new HashMap<>();
    private HashMap<String, Set<ActorRef>> CurrentReadingUsers = new HashMap<>();
    private HashMap<String, ActorRef> CurrentWriteUsers = new HashMap<>();


	private HashMap<String, ActorRef> RemoteManagerSourceList = new HashMap<>();
	private HashMap<Object, Integer> potentialRemoteManagerList = new HashMap<>();
	private HashMap<ActorRef, Map<String,Object>> remoteManagerResouceMessage = new HashMap<>();
	@Override
	public void onReceive(Object msg) throws Exception {
		//System.out.println(getSelf().toString()+"-"+"My message: "+msg.toString());
		//InitialLocalResources
        if(msg instanceof AccessReleaseMsg){

          //  System.out.println("access release msg");
            AccessReleaseMsg message = (AccessReleaseMsg) msg;
            AccessRelease release = message.getAccessRelease();
            log(LogMsg.makeAccessReleaseReceivedLogMsg(message.getSender(), getSelf(), release));
            String Rname=release.getResourceName();
            if(localResources.containsKey(Rname)){
              //  System.out.println("*local source contain");

                if(release.getType() == AccessType.CONCURRENT_READ){
                    //System.out.println("in side current read");
                    if(CurrentReadingUsers.containsKey(Rname) && CurrentReadingUsers.get(Rname).contains(message.getSender())){
                        log(LogMsg.makeAccessReleasedLogMsg(message.getSender(), getSelf(), release));
                        CurrentReadingUsers.get(Rname).remove(message.getSender());
                        if(disableMsg.containsKey(Rname) && !CurrentWriteUsers.containsKey(Rname) && CurrentReadingUsers.get(Rname).isEmpty()){
                         //   System.out.println("cr check1");
                            localResources.get(Rname).disable();
                            log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), Rname, ResourceStatus.DISABLED));
                            for(ManagementRequestMsg requesti: disableMsg.get(Rname) ){
                                log(LogMsg.makeManagementRequestGrantedLogMsg(requesti.getReplyTo(), getSelf(), requesti.getRequest()));
                                requesti.getReplyTo().tell(new ManagementRequestGrantedMsg(requesti), getSelf());
                            }
                            disableMsg.remove(Rname);
                        }
                        if(localResources.get(Rname).getStatus()!=ResourceStatus.DISABLED&&!disableMsg.containsKey(Rname)) {
                            // System.out.println("cr chgeck2");

                            //processing Q

                            Queue<AccessRequestMsg> currentQueue = AcessReqestMsgQueue.get(Rname);
                            boolean checker = true;
                            while (checker && AcessReqestMsgQueue.containsKey(Rname)
                                    && currentQueue != null
                                    && !currentQueue.isEmpty()
                            ) {
                                AccessRequestMsg tempRequest = currentQueue.peek();
                                AccessRequest accessInfo = tempRequest.getAccessRequest();
                                ActorRef user = tempRequest.getReplyTo();
                                String tempRname = accessInfo.getResourceName();
                                if (accessInfo.getType() == AccessRequestType.EXCLUSIVE_WRITE_BLOCKING) {
                                    //**might be issues here are those cases enough?

                                    if (!CurrentWriteUsers.containsKey(tempRname) || noOneOrJustMeReading(tempRname, user)) {
                                        currentQueue.poll();
                                        CurrentWriteUsers.put(tempRname, user);
                                        log(LogMsg.makeAccessRequestGrantedLogMsg(user, getSelf(), accessInfo));
                                        user.tell(new AccessRequestGrantedMsg(tempRequest), getSelf());
                                    } else {
                                        checker = false;
                                    }

                                } else {
                                    //you can look at her (read block)
                                    if (!CurrentWriteUsers.containsKey(tempRname)
                                            || CurrentWriteUsers.get(tempRname).equals(user)) {
                                        if (CurrentReadingUsers.containsKey(tempRname)) {
                                            CurrentReadingUsers.get(tempRname).add(user);
                                        } else {
                                            CurrentReadingUsers.put(tempRname, new HashSet<ActorRef>());
                                            CurrentReadingUsers.get(tempRname).add(user);
                                        }
                                        log(LogMsg.makeAccessRequestGrantedLogMsg(user, getSelf(), accessInfo));
                                        currentQueue.poll();
                                        user.tell(new AccessRequestGrantedMsg(accessInfo), getSelf());
                                    } else {
                                        checker = false;
                                    }
                                }
                            }
                        }

                        //processsing over

                    }else{
                        // System.out.println("makeAccessReleaseIgnoredLogMsg"+message.getSender().toString());
                        log(LogMsg.makeAccessReleaseIgnoredLogMsg(message.getSender(), getSelf(), release));
                    }
                }else{
                    if(CurrentWriteUsers.containsKey(Rname) && CurrentWriteUsers.get(Rname).equals(message.getSender())){
                        // System.out.println("inside writer")
                        log(LogMsg.makeAccessReleasedLogMsg(message.getSender(), getSelf(), release));
                        CurrentWriteUsers.remove(release.getResourceName());
                        if(disableMsg.containsKey(Rname) && !CurrentWriteUsers.containsKey(Rname) && CurrentReadingUsers.get(Rname).isEmpty()){
                            // System.out.println("disabling it");
                            localResources.get(Rname).disable();
                            log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), Rname, ResourceStatus.DISABLED));
                            for(ManagementRequestMsg requesti: disableMsg.get(Rname) ){
                                log(LogMsg.makeManagementRequestGrantedLogMsg(requesti.getReplyTo(), getSelf(), requesti.getRequest()));
                                requesti.getReplyTo().tell(new ManagementRequestGrantedMsg(requesti), getSelf());
                            }
                            disableMsg.remove(Rname);
                        }
                     
                        //processing Q
                        if(localResources.get(Rname).getStatus()!=ResourceStatus.DISABLED&&!disableMsg.containsKey(Rname)){
                        Queue<AccessRequestMsg> currentQueue=AcessReqestMsgQueue.get(Rname);
                        boolean checker =true;
                        while(   checker&& AcessReqestMsgQueue.containsKey(Rname)
                                &&currentQueue!=null
                                &&!currentQueue.isEmpty()
                        ){
                            AccessRequestMsg tempRequest=currentQueue.peek();
                            AccessRequest accessInfo=tempRequest.getAccessRequest();
                            ActorRef user=tempRequest.getReplyTo();
                            String tempRname=accessInfo.getResourceName();
                            if(accessInfo.getType()==AccessRequestType.EXCLUSIVE_WRITE_BLOCKING){
                                //**might be issues here are those cases enough?

                                    if(!CurrentWriteUsers.containsKey(tempRname)||noOneOrJustMeReading(tempRname,user)){
                                        currentQueue.poll();
                                        CurrentWriteUsers.put(tempRname, user);
                                        log(LogMsg.makeAccessRequestGrantedLogMsg(user,getSelf(),accessInfo));
                                        user.tell(new AccessRequestGrantedMsg(tempRequest), getSelf());
                                    }else{
                                        checker=false;
                                    }

                            }
                            else{
                                //you can look at her (read block)
                                if(!CurrentWriteUsers.containsKey(tempRname)
                                        ||CurrentWriteUsers.get(tempRname).equals(user))
                                {
                                    if(CurrentReadingUsers.containsKey(tempRname)){
                                        CurrentReadingUsers.get(tempRname).add(user);
                                    }else{
                                        CurrentReadingUsers.put(tempRname,new HashSet<ActorRef>());
                                        CurrentReadingUsers.get(tempRname).add(user);
                                    }
                                    log(LogMsg.makeAccessRequestGrantedLogMsg(user,getSelf(),accessInfo));
                                    currentQueue.poll();
                                    user.tell(new AccessRequestGrantedMsg(accessInfo),getSelf());
                                }else{
                                    checker=false;
                                }
                            }
                        }
                        }
                        //processsing over
                    }else{
                        log(LogMsg.makeAccessReleaseIgnoredLogMsg(message.getSender(), getSelf(), release));
                    }
                }
            }
            else{
                //System.out.println("*remote contain");

                if(!RemoteManagerSourceList.containsKey(Rname)){
                    log(LogMsg.makeAccessReleaseIgnoredLogMsg(message.getSender(), getSelf(), release));
                }else{
                    log(LogMsg.makeAccessReleaseForwardedLogMsg(getSelf(), RemoteManagerSourceList.get(Rname), release));
                    RemoteManagerSourceList.get(Rname).tell(msg, getSelf());
                }
            }
        }
       else if(msg instanceof ManagementRequestMsg){
           // System.out.println("management request msg");
            ManagementRequestMsg message = (ManagementRequestMsg) msg;
            ManagementRequest request = message.getRequest();
            log(LogMsg.makeManagementRequestReceivedLogMsg(message.getReplyTo(), getSelf(), request));
            String Rname=request.getResourceName();
            ActorRef From = message.getReplyTo();

            if(localResources.containsKey(Rname)){
                // System.out.println("in side local resource checking");
                if(request.getType() == ManagementRequestType.ENABLE){
                    // System.out.println("enabling it");

                    if(localResources.get(Rname).getStatus() == ResourceStatus.DISABLED){
                        // System.out.println("check2");
                        log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), Rname, ResourceStatus.ENABLED));
                        localResources.get(Rname).enable();

                        log(LogMsg.makeManagementRequestGrantedLogMsg(From, getSelf(), request));
                        From.tell(new ManagementRequestGrantedMsg(request), getSelf());
                    }
                    if(disableMsg.containsKey(Rname)) {
                        // System.out.println("check 1");
                        log(LogMsg.makeManagementRequestGrantedLogMsg(From, getSelf(), request));
                        From.tell(new ManagementRequestGrantedMsg(request), getSelf());
                    }
                }
                else
                {
                    // System.out.println("disabling it");
                    if(localResources.get(Rname).getStatus() != ResourceStatus.DISABLED){
                        if(      !CurrentWriteUsers.containsKey(Rname) &&
                                (!CurrentReadingUsers.containsKey(Rname) || CurrentReadingUsers.get(Rname).isEmpty()) &&
                                (!AcessReqestMsgQueue.containsKey(Rname) || AcessReqestMsgQueue.get(Rname).isEmpty()))
                        {
                            // System.out.println("case 1");
                            log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), Rname, ResourceStatus.DISABLED));
                            localResources.get(Rname).disable();

                            log(LogMsg.makeManagementRequestGrantedLogMsg(From, getSelf(), request));
                            getSender().tell(new ManagementRequestGrantedMsg(request), getSelf());

                        }else if (CurrentWriteUsers.containsKey(Rname) ||
                                CurrentReadingUsers.get(Rname).contains(From) ||
                                (AcessReqestMsgQueue.containsKey(Rname) && !AcessReqestMsgQueue.get(Rname).isEmpty())
                        ){
                            // System.out.println("case 2");
                            log(LogMsg.makeManagementRequestDeniedLogMsg(From, getSelf(), request, ManagementRequestDenialReason.ACCESS_HELD_BY_USER));
                            From.tell(new ManagementRequestDeniedMsg(request, ManagementRequestDenialReason.ACCESS_HELD_BY_USER), getSelf());
                        }else{
                            // System.out.println("case 3");
                            if(!disableMsg.containsKey(Rname)) {
                                disableMsg.put(Rname, new HashSet<ManagementRequestMsg>());
                            }
                            disableMsg.get(Rname).add((ManagementRequestMsg) msg);
                        }
                    }
                }
            }else{
                // System.out.println("other manager has it");
                if(RemoteManagerSourceList.containsKey(Rname)){
                    // System.out.println(Rname+"has it forwarding");
                    log(LogMsg.makeManagementRequestForwardedLogMsg(getSelf(), RemoteManagerSourceList.get(Rname), request));
                    RemoteManagerSourceList.get(Rname).tell(msg, getSelf());

                }else{
                    // System.out.println("dont have it looking.");

                    potentialRemoteManagerList.put(msg,remoteManagers.size());
                    WhoHasResourceRequestMsg future = new WhoHasResourceRequestMsg(Rname);
                    for(ActorRef RM: remoteManagers){
                        if(!remoteManagerResouceMessage.containsKey(RM)) {
                            remoteManagerResouceMessage.put(RM, new HashMap<String, Object>());
                        }
                        remoteManagerResouceMessage.get(RM).put(Rname,  msg);

                        RM.tell(future, getSelf());
                    }
                }


            }
        }
		else if(msg instanceof AddInitialLocalResourcesRequestMsg){
           // System.out.println("initial local resource request");
			for(Resource rs:((AddInitialLocalResourcesRequestMsg) msg).getLocalResources()){
				rs.enable();

				log(LogMsg.makeLocalResourceCreatedLogMsg(getSelf(), rs.name));
				localResources.put(rs.getName(),rs);
                log(LogMsg.makeResourceStatusChangedLogMsg(getSelf(), rs.name, ResourceStatus.ENABLED));
			}
			getSender().tell(
					new AddInitialLocalResourcesResponseMsg((AddInitialLocalResourcesRequestMsg)msg), getSelf());
		}
		//setting up LocalUsers
		else if(msg instanceof AddLocalUsersRequestMsg){
           // System.out.println("add local user");
			for(ActorRef actor:((AddLocalUsersRequestMsg) msg).getLocalUsers()){
				//log(LogMsg.makeUserStartLogMsg(actor));

				localUsers.add(actor);
			}
			getSender().tell(
					new AddLocalUsersResponseMsg((AddLocalUsersRequestMsg)msg), getSelf());
		}
		//setting up remote manager list
		else if(msg instanceof AddRemoteManagersRequestMsg){
          //  System.out.println("add remote manager request");
			for(ActorRef actor:((AddRemoteManagersRequestMsg) msg).getManagerList()){
				if(!(getSelf().equals(actor))){
					remoteManagers.add(actor);
				}
			}
			getSender().tell(
					new AddRemoteManagersResponseMsg((AddRemoteManagersRequestMsg)msg), getSelf());
		}
		//access request
		else if(msg instanceof AccessRequestMsg){

            // System.out.println("AccessRequestMsg");
			AccessRequestMsg message=(AccessRequestMsg) msg;
			AccessRequest request = message.getAccessRequest();
			ActorRef replyTo = message.getReplyTo();
			log(LogMsg.makeAccessRequestReceivedLogMsg(replyTo, getSelf(), request));

			String Rname = request.getResourceName();
          //  System.out.println("acess request msg on:"+request.getResourceName());
			//System.out.println("inside access request: "+Rname); Rname = printer_0
            //ur in the local game
			if(localResources.containsKey(Rname)){
			    //System.out.println("*local contain");
                //just got rejected
				if(ResourceStatus.DISABLED==localResources.get(Rname).getStatus()|| disableMsg.containsKey(Rname)){

					log(LogMsg.makeAccessRequestDeniedLogMsg(
							replyTo, getSelf(), request, AccessRequestDenialReason.RESOURCE_DISABLED)
					);

					replyTo.tell(
							new AccessRequestDeniedMsg(request,AccessRequestDenialReason.RESOURCE_DISABLED),
							getSelf()
					);
					return;
				}
				//she is holding you up (blocking)
				AccessRequestType type=request.getType();
				if(type == AccessRequestType.EXCLUSIVE_WRITE_BLOCKING
						||type == AccessRequestType.CONCURRENT_READ_BLOCKING)
				{

                    //she's putting you in her list
					if(!AcessReqestMsgQueue.containsKey(Rname)){
                        AcessReqestMsgQueue.put(Rname, new LinkedList<>());
					}
					AcessReqestMsgQueue.get(Rname).add(message);


                    //issue here
                    if(localResources.get(Rname).getStatus() != ResourceStatus.DISABLED&&!disableMsg.containsKey(Rname)){
                        //she is deciding
                        //que processing
                        Queue<AccessRequestMsg> currentQueue = AcessReqestMsgQueue.get(Rname);
                        boolean checker = true;
                        while (checker && AcessReqestMsgQueue.containsKey(Rname)
                                && currentQueue != null
                                && !currentQueue.isEmpty()
                        ) {
                            AccessRequestMsg tempRequest = currentQueue.peek();
                            AccessRequest accessInfo = tempRequest.getAccessRequest();
                            ActorRef user = tempRequest.getReplyTo();
                            String tempRname = accessInfo.getResourceName();
                            if (accessInfo.getType() == AccessRequestType.EXCLUSIVE_WRITE_BLOCKING) {
                                //**might be issues here are those cases enough?

                                if (!CurrentWriteUsers.containsKey(tempRname) || noOneOrJustMeReading(tempRname, user)) {
                                    currentQueue.poll();
                                    CurrentWriteUsers.put(tempRname, user);
                                    log(LogMsg.makeAccessRequestGrantedLogMsg(user, getSelf(), accessInfo));
                                    user.tell(new AccessRequestGrantedMsg(tempRequest), getSelf());
                                } else {
                                    checker = false;
                                }

                            } else {
                                //you can look at her (read block)
                                if (!CurrentWriteUsers.containsKey(tempRname)
                                        || CurrentWriteUsers.get(tempRname).equals(user)) {
                                    if (CurrentReadingUsers.containsKey(tempRname)) {
                                        CurrentReadingUsers.get(tempRname).add(user);
                                    } else {
                                        CurrentReadingUsers.put(tempRname, new HashSet<ActorRef>());
                                        CurrentReadingUsers.get(tempRname).add(user);
                                    }
                                    log(LogMsg.makeAccessRequestGrantedLogMsg(user, getSelf(), accessInfo));
                                    currentQueue.poll();
                                    user.tell(new AccessRequestGrantedMsg(accessInfo), getSelf());
                                } else {
                                    checker = false;
                                }
                            }
                        }
                    }

                    //end processing

					return;
				}
				//you got a date. non blocking cases:
                if(       !AcessReqestMsgQueue.containsKey(Rname)
                        || AcessReqestMsgQueue.get(Rname) == null
                        || AcessReqestMsgQueue.get(Rname).isEmpty())
                {
                    if(type==AccessRequestType.EXCLUSIVE_WRITE_NONBLOCKING){
                        //**might be issues here need to use %% and check for if it is current user
                       // System.out.println("*non blocking write:"+Rname);
                        if(CurrentWriteUsers.containsKey(Rname) || !noOneOrJustMeReading( Rname,replyTo)){
                            log(LogMsg.makeAccessRequestDeniedLogMsg(replyTo,
                                    getSelf(), request, AccessRequestDenialReason.RESOURCE_BUSY));
                            replyTo.tell(new AccessRequestDeniedMsg(request,AccessRequestDenialReason.RESOURCE_BUSY), getSelf());
                        }else{
                            CurrentWriteUsers.put(Rname, replyTo);
                            log(LogMsg.makeAccessRequestGrantedLogMsg(replyTo,getSelf(),request));
                            replyTo.tell(new AccessRequestGrantedMsg((AccessRequestMsg)msg), getSelf());
                        }
                    }else if(type==AccessRequestType.CONCURRENT_READ_NONBLOCKING){
                       // System.out.println("*non blocking read:"+Rname);
                        if(CurrentWriteUsers.containsKey(Rname) && !CurrentWriteUsers.get(Rname).equals(replyTo)){
                            log(LogMsg.makeAccessRequestDeniedLogMsg(replyTo,
                                    getSelf(), request, AccessRequestDenialReason.RESOURCE_BUSY));
                            replyTo.tell(new AccessRequestDeniedMsg(request,AccessRequestDenialReason.RESOURCE_BUSY), getSelf());
                        }else{
                            if(CurrentReadingUsers.containsKey(Rname)){
                                CurrentReadingUsers.get(Rname).add(replyTo);
                            }else{
                                CurrentReadingUsers.put(Rname, new HashSet<ActorRef>());
                                CurrentReadingUsers.get(Rname).add(replyTo);
                            }

                            log(LogMsg.makeAccessRequestGrantedLogMsg(replyTo,getSelf(),request));
                            replyTo.tell(new AccessRequestGrantedMsg((AccessRequestMsg)msg), getSelf());

                        }
                    }else{
                        System.out.println("********you aren't not suppose to hit here**********************************");
                    }
                }else
                    {
                        log(LogMsg.makeAccessRequestDeniedLogMsg(
                                replyTo,
                                getSelf(),
                                request,
                                AccessRequestDenialReason.RESOURCE_BUSY));
                        replyTo.tell(new AccessRequestDeniedMsg(request,AccessRequestDenialReason.RESOURCE_BUSY),
                                getSelf());
                    }
			}
			//the remote cases, you a global player
			else{
               // System.out.println("*remonte contain");
                if(!RemoteManagerSourceList.containsKey(Rname)){

                    potentialRemoteManagerList.put((AccessRequestMsg)msg,remoteManagers.size());
                    WhoHasResourceRequestMsg ms = new WhoHasResourceRequestMsg(Rname);
                    for(ActorRef RM: remoteManagers){
                        if(!remoteManagerResouceMessage.containsKey(RM)) {
                            remoteManagerResouceMessage.put(RM, new HashMap<String, Object>());
                        }
                        remoteManagerResouceMessage.get(RM).put(Rname, msg);
                        RM.tell(ms, getSelf());
                    }

                }else{
                    log(LogMsg.makeAccessRequestForwardedLogMsg(getSelf(), RemoteManagerSourceList.get(Rname), request));
                    RemoteManagerSourceList.get(Rname).tell(msg, getSelf());
                }
            }
		}
		//looking for girlfriend
		else if(msg instanceof WhoHasResourceRequestMsg){
            //System.out.println("who has resource request msg");
            String Rname=((WhoHasResourceRequestMsg)msg).getResourceName();
            if(!localResources.containsKey(Rname)){
                getSender().tell( new WhoHasResourceResponseMsg(Rname, false, getSelf()), getSelf());
            }else{
                getSender().tell(new WhoHasResourceResponseMsg(Rname, true, getSelf()), getSelf());
            }
        }

        //girlfriend holla back
        else if(msg instanceof WhoHasResourceResponseMsg){
           // System.out.println("who has resource resqonse msg");
            WhoHasResourceResponseMsg message = (WhoHasResourceResponseMsg) msg;
            Object oldMessage = remoteManagerResouceMessage.get(message.getSender()).get(message.getResourceName());

            if(message.getResult()){

                log(LogMsg.makeRemoteResourceDiscoveredLogMsg(getSelf(), message.getSender(), message.getResourceName()));
                RemoteManagerSourceList.put(message.getResourceName(), message.getSender());

                if(oldMessage instanceof ManagementRequestMsg) {
                    log(LogMsg.makeManagementRequestForwardedLogMsg(getSelf(), message.getSender(), ((ManagementRequestMsg) oldMessage).getRequest()));
                }
                else if(oldMessage instanceof AccessRequestMsg){
                    log(LogMsg.makeAccessRequestForwardedLogMsg(getSelf(), message.getSender(), ((AccessRequestMsg)oldMessage).getAccessRequest()));
                }
                else{
                    System.out.println("********you aren't not suppose to hit here**********WhoHasResourceResponseMsg************************");
                }

                message.getSender().tell(oldMessage, getSelf());
                potentialRemoteManagerList.remove(oldMessage);
                remoteManagerResouceMessage.get(message.getSender()).remove(oldMessage);

            }else {
                if(potentialRemoteManagerList.get(oldMessage)!=0){
                    potentialRemoteManagerList.put(oldMessage, potentialRemoteManagerList.get(oldMessage)-1);
                }

                if(potentialRemoteManagerList.get(oldMessage) == 0){
                    if(oldMessage instanceof ManagementRequestMsg){

                        log(LogMsg.makeManagementRequestDeniedLogMsg(((ManagementRequestMsg) oldMessage).getReplyTo(),
                                getSelf(), ((ManagementRequestMsg) oldMessage).getRequest(), ManagementRequestDenialReason.RESOURCE_NOT_FOUND));

                        ActorRef replyTo = ((ManagementRequestMsg) oldMessage).getReplyTo();

                        replyTo.tell(new ManagementRequestDeniedMsg(((ManagementRequestMsg) oldMessage).getRequest()
                                ,ManagementRequestDenialReason.RESOURCE_NOT_FOUND), getSelf());

                        }else{
                        log(LogMsg.makeAccessRequestDeniedLogMsg(((AccessRequestMsg) oldMessage).getReplyTo(),
                                getSelf(), ((AccessRequestMsg) oldMessage).getAccessRequest(), AccessRequestDenialReason.RESOURCE_NOT_FOUND));


                        ActorRef replyTo = ((AccessRequestMsg) oldMessage).getReplyTo();


                        replyTo.tell(new AccessRequestDeniedMsg(((AccessRequestMsg) oldMessage).getAccessRequest()
                                ,AccessRequestDenialReason.RESOURCE_NOT_FOUND), getSelf());



                    }
                }
            }
        }

	}

    public Boolean noOneOrJustMeWriting(String Rname,ActorRef user){
        if(CurrentWriteUsers.containsKey(Rname)){
            for(ActorRef user1:CurrentReadingUsers.get(Rname)){
                if(!user1.equals(user)){
                    return false;
                }
            }
        }
        return true;
    }


    public Boolean noOneOrJustMeReading(String Rname,ActorRef user){
	    if(CurrentReadingUsers.containsKey(Rname)){
	        for(ActorRef user1:CurrentReadingUsers.get(Rname)){
	            if(!user1.equals(user)){
	                return false;
                }
            }
        }
        return true;
    }


}
