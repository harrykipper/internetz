package internetz;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import repast.simphony.context.Context;
import repast.simphony.context.DefaultContext;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.engine.schedule.ISchedule;
import repast.simphony.engine.schedule.Schedule;
import repast.simphony.engine.schedule.ScheduleParameters;
import repast.simphony.engine.schedule.ScheduledMethod;
import repast.simphony.parameter.Parameters;
import repast.simphony.random.RandomHelper;
import repast.simphony.space.graph.Network;
import repast.simphony.space.graph.RepastEdge;
import repast.simphony.util.ContextUtils;


public class InternetzCtx extends DefaultContext {
	//Parameters param = null;
	int agentCount = 0;
	int memeCount = 0;
	double pctPublishers = 0.0;
	int groups = 0;
	int readingCapacity;
	int memeBrk = 0;
	int agntBrk = 0;
	int maxbeliefs = 0;
	private static double dampingFactor = 0.85;
	Vector totCommunities = new Vector();
	private static int agentsToAdd = 4;

	Hashtable allMemes = new Hashtable(); 


	public InternetzCtx (){
		super("InternetzCtx");
		Parameters param = RunEnvironment.getInstance().getParameters();
		agentCount = (Integer)param.getValue("agent_count");
		memeCount = (Integer)param.getValue("meme_count");
		pctPublishers = (Double)param.getValue("pctpubli");
		groups = (Integer) param.getValue("cultGroups");
		maxbeliefs = (Integer) param.getValue("maxbelief");


		NetworkBuilder<Object> netBuilder = new NetworkBuilder<Object>("artifacts", (Context<Object>) this, true);
		netBuilder.buildNetwork();
		NetworkBuilder<Object> netBuilderMM = new NetworkBuilder<Object>("artimemes", (Context<Object>) this, false);
		netBuilderMM.buildNetwork();
		NetworkBuilder<Object> netBuilderBlf = new NetworkBuilder<Object>("beliefs", (Context<Object>) this, false);
		netBuilderBlf.buildNetwork();
		NetworkBuilder<Object> netBuilderMem = new NetworkBuilder<Object>("memorys", (Context<Object>) this, false);
		netBuilderMem.buildNetwork();
		NetworkBuilder<Object> netBuilderSN = new NetworkBuilder<Object>("twitter", (Context<Object>) this, true);
		netBuilderSN.buildNetwork();

		if (groups > 1) {
			agntBrk = agentCount/groups;
			memeBrk = memeCount/groups;
			// System.out.println(memeBrk+"  "+agntBrk);
		}

		for (int grp=0; grp<groups; grp++) {
			ArrayList community = new ArrayList();
			totCommunities.add(community);
		}

		// totGroups.get(group)
		for (int i=0; i<memeCount; i++) {
			Meme meme = new Meme();
			this.add(meme);
			meme.setID(i);
			allMemes.put(i, meme);
			if (groups>1) {
				int whichgrp = (int)i/memeBrk;
				ArrayList community = (ArrayList) totCommunities.get(whichgrp);
				community.add(meme);
				meme.setGrp(whichgrp);
				//System.out.println("I am a meme in group "+ whichgrp);
			}
		}

		Network belief = (Network) this.getProjection("beliefs");
		Network memory = (Network) this.getProjection("memorys");
		Network artifct = (Network) this.getProjection("artifacts");

		addAgent(agentCount,true);

		String algo = (String)param.getValue("filteringalgo");

		System.out.println("Number of memes created "+ this.getObjects(Meme.class).size());
		System.out.println("Number of agents created "+ this.getObjects(Agent.class).size());
		System.out.println("Algorithm tested: "+ algo);

		/*	ISchedule schedule = RunEnvironment.getInstance().getCurrentSchedule();
		ScheduleParameters schedParams;
		schedParams = ScheduleParameters.createAtEnd(ScheduleParameters.FIRST_PRIORITY);//.createOneTime(103);
		// OutputRecorder recorder = new OutputRecorder(this, simulation_mode);
		schedule.schedule(schedParams,this,  "outputSNSData", "record");
		 */
	}


	public void addAgent(int agentCnt, boolean concentrate) {
		Parameters param = RunEnvironment.getInstance().getParameters();
		//this this = (this)ContextUtils.getContext(this);	
		Network belief = (Network)getProjection("beliefs");
		Network memory = (Network)getProjection("memorys");
		Network artimeme = (Network)getProjection("artimemes");
		Network artifact = (Network<Artifact>)getProjection("artifacts");		
		Network sns = (Network)getProjection("twitter");
		for (int i=0; i<agentCnt; i++) {
			boolean ispublisher = false;
			RandomHelper.createPoisson((Integer)param.getValue("avgcap"));
			readingCapacity = RandomHelper.getPoisson().nextInt();
			if (RandomHelper.nextDoubleFromTo(0, 1) < pctPublishers) ispublisher = true;
			Agent agent = new Agent();
			this.add(agent);
			agent.setReadingCapacity(readingCapacity);
			agent.setPublisher(ispublisher);
			// agent.setID(i);
			RandomHelper.createPoisson(maxbeliefs);
			int howmany = RandomHelper.getPoisson().nextInt();
			ArrayList<Meme> mymemes = new ArrayList();
			// double tick = RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
			if (groups>1) {
				int whichgrp=3;
				// if (concentrate == false) whichgrp = (int)i/agntBrk;
				if (concentrate==false) {
					if (this.getObjects(Agent.class).size()>=agentCount) whichgrp = RandomHelper.nextIntFromTo(0, groups-2);
					else whichgrp = RandomHelper.nextIntFromTo(0, groups-1);
				}
				agent.setGroup(whichgrp);
				ArrayList mycommunity = (ArrayList) totCommunities.get(whichgrp);
				double ninetyPct = 0.9*howmany;
				int j = 0;						
				while(j < ninetyPct) {
					Meme thismeme = (Meme) mycommunity.get(RandomHelper.nextIntFromTo(0, mycommunity.size()-1));
					if (!belief.isAdjacent(thismeme, agent)&&isItSuitable(thismeme,agent)) {
						mymemes.add(thismeme);
						j++;
					}
				}
				int k = 0;
				double tenPct = (howmany-ninetyPct);
				while (k < tenPct) {
					Iterator<Meme> allmemes = this.getRandomObjects(Meme.class, howmany).iterator();
					while (allmemes.hasNext()) {
						Meme thismeme = (Meme) allmemes.next();
						if (!mycommunity.contains(thismeme)&&!belief.isAdjacent(thismeme, agent) && k < tenPct) {
							mymemes.add(thismeme);
							k++;
						}
					}
				}
			} else {
				Iterator allmemes = this.getRandomObjects(Meme.class, howmany).iterator();
				while (allmemes.hasNext()) mymemes.add((Meme) allmemes.next());
			}

			attributeMemes(agent,mymemes);
		}
	}

	public void attributeMemes(Agent agent, ArrayList memes) {
		Network belief = (Network)getProjection("beliefs");
		int allmms = memes.size();
		for (int h=0; h<allmms;h++ ) {
			Meme target = (Meme)memes.get(h);
			//System.out.println("I am now adding meme: "+target);
			double wght = RandomHelper.nextDoubleFromTo(0.1, 1);
			belief.addEdge(agent,target,wght);
			// darli a caso. si.
		}
	}

	public boolean isItSuitable(Meme meme, Agent agent) {
		Network belief = (Network)getProjection("beliefs");
		Boolean suitable = true;
		int opp = 5000 - meme.getID();
		// Iterator opposite = new PropertyEquals(context, "id", opp).query().iterator();
		Iterator agentBlfs = belief.getAdjacent(agent).iterator();
		while (agentBlfs.hasNext()) {
			Meme thisblf = (Meme) agentBlfs.next();
			if (thisblf.getID()==opp) suitable = false; 
		}
		return suitable;
	}

	public Hashtable getMemez() {
		return allMemes;
	}

	public int getAgents() {
		return this.getObjects(Agent.class).size();
	}

	public int getArtifacts() {
		return this.getObjects(Artifact.class).size();
	}


	@ScheduledMethod(start = 1, interval = 1)
	public void updatePageRnk() {   // Adapted from the netlogo 'diffusion' code (fingers crossed)
		Network artifact = (Network)this.getProjection("artifacts");
		double increment = 0;
		for (Object arti : this.getObjects(Artifact.class)) ((Artifact) arti).setNewRank(0);
		for (Object artifct : this.getObjects(Artifact.class)) {
			if (artifact.getOutDegree(artifct) > 0) {
				increment = ((Artifact) artifct).getRank()/artifact.getOutDegree(artifct);
				for (Object outNeighbor : artifact.getSuccessors(artifct)) 
				{
					Artifact sequent = (Artifact) outNeighbor;
					sequent.setNewRank(sequent.getNewRank()+increment);
				}
			} else {
				increment = ((Artifact) artifct).getRank()/this.getObjects(Artifact.class).size();
				for (Object allOtherArts : this.getObjects(Artifact.class)) {
					Artifact sequent = (Artifact) allOtherArts;
					double oldrnk = sequent.getNewRank();
					sequent.setNewRank(oldrnk+increment);
				}
			}
		}

		for (Object everySingleArtifact : this.getObjects(Artifact.class)) {
			Artifact artfc = (Artifact) everySingleArtifact;
			double rnk = (1-dampingFactor) / (this.getObjects(Artifact.class).size() + (dampingFactor*artfc.getNewRank())) ;
			artfc.setRank(rnk);
		}
	}



	//THIS IMPLEMENTS "NEVERENDING SEPTEMBER"
	//@ScheduledMethod(start = 550)
	//public void september() {
	//	addAgent(100,true);
	//}


	@ScheduledMethod(start = 2000, priority=0)
	public void outputSNSData() throws IOException {
		Network sns = (Network)this.getProjection("twitter");
		StringBuilder dataToWrite = new StringBuilder(); 
		dataToWrite.append("Source; Destination\n"); // This is the header for the csv file
		Iterator allEdges = sns.getEdges().iterator();

		for (Object obj : sns.getEdges()) {
			if (obj instanceof RepastEdge) {
				RepastEdge edge = (RepastEdge) obj;
				Object src = edge.getSource();
				Object tar = edge.getTarget();
				//String srcName = idMap.get(src);
				//String tarName = idMap.get(tar);
				//double weight = edge.getWeight();
				dataToWrite.append(((Agent) src).getID() +";"+((Agent) tar).getID()+"\n");
				System.out.println(((Agent) src).getID() +";"+((Agent) tar).getID()+"\n");
			}
		}
		// Now write this data to a file
		BufferedWriter bw = new BufferedWriter(new FileWriter(new File("socialnetwork.edgelist")));
		bw.write(dataToWrite.toString());
		bw.close();
	}


	//UNCOMMENT THE FOLLOWING TO GET A DECREASING INFLOW OF USERS IN THE SYSTEM
	@ScheduledMethod(start=10, interval=2)
	public void inflow() {
		double time = RunEnvironment.getInstance().getCurrentSchedule().getTickCount();
		if (time > 400) agentsToAdd/=2;
		if (time > 800) agentsToAdd=0;
		if (agentsToAdd>0) addAgent(agentsToAdd,false);
	}
}