package dc_project1_bellman_ford;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;


/**
 * Team Members:
 * Sujal Patel (ssp150930)
 * Harshil Shah (hxs155030)
 * Sagar Mehta (sam150930)
 * 
 * This is the individual process class which runs bellman ford algorithm. 
 */
public class Processes implements Runnable {

	// Current process id
	private int ProcessId;
	/*
	 * qMaster -> write READY message to this Q. 
	 * qRound -> Receive NEXT signal from Master process in this Q. 
	 * qIn -> Interprocess Q. 
	 * qDone -> Root signals to Master process about completion of converge cast and algorithm
	 * qReadyToSend -> Write in this Queue READY message to let Master know you want to send the messages on link now.
	 */
	private BlockingQueue<Message> qMaster, qRound, qIn, qDone, qReadyToSend;
	// enum to express current state of neighbor
	private enum State{
		EXPLORE, NACK, DONE;
	}
	// List of all neighbors of current process
	private ArrayList<Edge> edges;
	private int parentID;
	private int distanceFromRoot;
	private boolean isRoot, exploreToSend, firstRound, addReadyMsg = false;
	// List in which messages to send in next round are populated.
	private HashMap<Processes, Message> sendList = new HashMap<Processes, Message>();
	// Save the state of the neighbors.
	private HashMap<Integer, State> stateList = new HashMap<Integer, State>();
	// List in which ID's of processes who sent EXPLORE message to this process are stored.
	private ArrayList<Integer> exploreIDs = new ArrayList<Integer>();
	
	// For debugging purposes
	int roundNo = 0;
	private boolean debugStatements = false;
	
	// Constructor
	public Processes(int processId) {
		this.ProcessId = processId;
		edges = new ArrayList<Edge>();
		this.debugStatements = false;
	}

	// Process initialization function.
	public void Initialize() {
		this.exploreToSend = false;
		this.parentID = Integer.MIN_VALUE;
		this.firstRound = true;
		if (this.ProcessId == MasterProcess.rootProcessID) {
			this.distanceFromRoot = 0;
			this.isRoot = true;
		} else {
			this.distanceFromRoot = Integer.MAX_VALUE;
			this.isRoot = false;
		}
	}

	// Function to add message to this processes Interprocess Q
	public void writeToQIn(Message msg) {
		qIn.add(msg);
	}
	// Function to add single edge. For debugging purposes.
	public void addEdge(Edge e) {
		this.edges.add(e);
	}
	// Function to print parent ID
	public void printParentID() {
		System.out.println(this.parentID);
	}
	
	// getter/setter functions
	
	public ArrayList<Edge> getEdges() {
		return edges;
	}

	public void setEdges(ArrayList<Edge> edgeList) {
		edges = edgeList;
	}

	public int getDistanceFromRoot() {
		return distanceFromRoot;
	}

	public void setDistanceFromRoot(int distancefromRoot) {
		distanceFromRoot = distancefromRoot;
	}

	public void setQMaster(BlockingQueue<Message> qmaster) {
		qMaster = qmaster;
	}

	public void setQRound(BlockingQueue<Message> qround) {
		qRound = qround;
	}

	public void setQIn(BlockingQueue<Message> qin) {
		qIn = qin;
	}

	public BlockingQueue<Message> getQDone() {
		return qDone;
	}

	public void setQDone(BlockingQueue<Message> qdone) {
		qDone = qdone;
	}

	public BlockingQueue<Message> getQMaster() {
		return qMaster;
	}

	public BlockingQueue<Message> getQRound() {
		return qRound;
	}

	public BlockingQueue<Message> getQIn() {
		return qIn;
	}

	public int getProcessId() {
		return ProcessId;
	}

	public int getParentID() {
		return parentID;
	}

	public void setParentID(int parentid) {
		parentID = parentid;
	}

	public BlockingQueue<Message> getQReadyToSend() {
		return qReadyToSend;
	}

	public void setQReadyToSend(BlockingQueue<Message> qreadyToSend) {
		qReadyToSend = qreadyToSend;
	}
	
	// Function that runs the core process code
	@Override
	public void run() {
		// TODO Auto-generated method stub
		Initialize();
		while (true) {
			Message message = null;
			try {
				// check for the start of next round
				while (!(qRound.size() > 0));
				if (qRound.peek() != null)
					message = qRound.take();
				
				if (message.getMessageType() == Message.MessageType.NEXT) {
					
					roundNo++;
					
					this.addReadyMsg = false;
					
					if (this.isRoot && this.firstRound) {
						Processes neighbourProcess;
						Iterator<Edge> Iter = this.edges.iterator();
						while (Iter.hasNext()) {
							Edge E = Iter.next();
							neighbourProcess = E.getNeighbour(this);
							int Distance = distanceFromRoot + E.getWeight();
							message = new Message(this.ProcessId, Message.MessageType.EXPLORE, Distance, 'I');
							sendList.put(neighbourProcess, message);
							stateList.put(neighbourProcess.getProcessId(), State.EXPLORE);
						}
						this.firstRound = false;
					} else
						this.firstRound = false;
					
					this.exploreToSend = false;
					//Receive all incoming messages
					while (qIn.size() > 0) {
						
						try {
							message = qIn.take();
							//Explore message handler
							if (message.getMessageType() == Message.MessageType.EXPLORE) {
								
								// Relaxation step for Bellman-Ford Algorithm
								exploreIDs.add(message.getProcessId());
								if (this.distanceFromRoot > (int) message.getDistance()) {
									this.distanceFromRoot = (int) message.getDistance();
									this.parentID = message.getProcessId();
									this.exploreToSend = true;
								}
							}
							// DONE message handler
							if (message.getMessageType() == Message.MessageType.DONE) {
								int neighborID = message.getProcessId();
								this.stateList.replace(neighborID, State.DONE);
								
							}
							// NACK message handler
							if (message.getMessageType() == Message.MessageType.NACK) {
								int neighborID = message.getProcessId();
								this.stateList.replace(neighborID, State.NACK);
							}
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					// Now processing messages
					//sending NACK to unhelpful explore messages
					if(this.exploreIDs.size() > 0){
						for(int id : this.exploreIDs){
							if(id != this.parentID){
								message = new Message(this.ProcessId, Message.MessageType.NACK, Integer.MAX_VALUE, 'N');
								Processes neighbor;
								Iterator<Edge> iter = this.edges.iterator();
								while (iter.hasNext()) {
									Edge e = iter.next();
									neighbor = e.getNeighbour(this);
									if (neighbor.getProcessId() == id) {
										sendList.put(neighbor, message);
									}
								}
							}
						}
					}
					//Send EXPLORE to neighbors
					if(this.exploreToSend){
						Processes neighbor;
						Iterator<Edge> iter = this.edges.iterator();
						if(stateList.size() > 0)
							stateList.clear();
						while (iter.hasNext()) {
							Edge e = iter.next();
							message = new Message(this.ProcessId, Message.MessageType.EXPLORE, (this.distanceFromRoot + e.getWeight()) , 'E');
							neighbor = e.getNeighbour(this);
							if (neighbor.getProcessId() != this.parentID && neighbor.getProcessId() != MasterProcess.rootProcessID) {
								sendList.put(neighbor, message);
								stateList.put(neighbor.getProcessId(), State.EXPLORE);
							}
						}
					}
					//send DONE to parent
					boolean doneFlag = false;
					if(this.stateList.size() == 0 && this.parentID != Integer.MIN_VALUE)
						doneFlag = true;
					else{
						for(Entry<Integer, State> e : this.stateList.entrySet()){
							if((e.getValue() == State.NACK || e.getValue() == State.DONE))
							{
								doneFlag = true;
							}
							else
							{
								doneFlag = false;
								break;
							}
						}
					}
					
					if(doneFlag){
						if(this.isRoot){
							message = new Message(this.ProcessId, Message.MessageType.DONE, Integer.MAX_VALUE, 'D');
							qDone.add(message);
						}
						Processes ngbhr;
						Iterator<Edge> Iter = this.edges.iterator();
						while (Iter.hasNext()) {
							Edge E = Iter.next();
							message = new Message(this.ProcessId, Message.MessageType.DONE, Integer.MIN_VALUE , 'D');
							ngbhr = E.getNeighbour(this);
							if (ngbhr.getProcessId() == this.parentID) {
								sendList.put(ngbhr, message);
							}
						}
					}
					
					// Signal 'Ready to send' and wait.
					Message readyToSendMsg = new Message(this.ProcessId, Message.MessageType.READY, Integer.MIN_VALUE, 'R');
					synchronized (this) {
						qReadyToSend.add(readyToSendMsg);
					}
					while(qReadyToSend.size() != 0);
					// Send all the outgoing messages.
					if (sendList.size() > 0) {
						Iterator<Entry<Processes, Message>> iter = sendList.entrySet().iterator();
						while (iter.hasNext()) {
							Map.Entry<Processes, Message> pair = (Map.Entry<Processes, Message>) iter.next();
							Processes toSend = pair.getKey();
							Message toSendMsg = pair.getValue();
							toSend.writeToQIn(toSendMsg);
							if(this.debugStatements)
								System.out.println("*Round NO.: " + this.roundNo + " To: " + toSend.getProcessId() + " " + toSendMsg.debug() + "\n");
							iter.remove();
						}
					}					
					// Signal READY for next round
					Message readyMSG = new Message(this.ProcessId, Message.MessageType.READY, Integer.MIN_VALUE, 'R');
					synchronized (this) {
						if (!this.addReadyMsg) {
							qMaster.add(readyMSG);
							this.addReadyMsg = true;
						}
					}
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
}
