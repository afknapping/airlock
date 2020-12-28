package airlock.agent.graph;

import airlock.AirlockChannel;
import airlock.AirlockUtils;
import airlock.PokeResponse;
import airlock.agent.Agent;
import airlock.agent.graph.types.*;
import airlock.agent.graph.types.content.GraphContent;
import airlock.agent.group.GroupUtils;
import airlock.agent.group.types.GroupPolicy;
import airlock.errors.channel.AirlockAuthenticationError;
import airlock.errors.channel.AirlockRequestError;
import airlock.errors.channel.AirlockResponseError;
import airlock.errors.scry.ScryDataNotFoundException;
import airlock.errors.scry.ScryFailureException;
import airlock.errors.spider.SpiderFailureException;
import airlock.types.ShipName;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static airlock.AirlockUtils.gson;
import static airlock.AirlockUtils.map2json;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.StreamSupport.stream;

public class GraphAgent extends Agent {



	private final Set<Resource> keys;
	private final Map<Resource, Graph> graphs;

	// adapting from new landscape api https://github.com/urbit/urbit/blob/1895e807fdccd669dd0b514dff1c07aa3bfe7449/pkg/interface/src/logic/api/graph.ts
	// and also https://github.com/urbit/urbit/blob/51fd47e886092a842341df9da549f77442c56866/pkg/interface/src/types/graph-update.ts
	public GraphAgent(AirlockChannel urbit) {
		// todo handle state
		// https://github.com/urbit/urbit/blob/master/pkg/interface/src/logic/reducers/graph-update.js
		// https://github.com/urbit/urbit/blob/82851feaea21cdd04d326c80c4e456e9c4f9ca8e/pkg/interface/src/logic/store/store.ts
		// https://github.com/urbit/urbit/blob/82851feaea21cdd04d326c80c4e456e9c4f9ca8e/pkg/interface/src/logic/api/graph.ts
		super(urbit);
		this.keys = new HashSet<>();
		this.graphs = new HashMap<>();
		// todo potentially use a separate AgentState class. right now we'll just manually implement
		// todo custom dataclass for graph-update with all derivatives
	}

	public Map<Resource, Graph> getCurrentGraphs() {
		return graphs;
	}

	public Set<Resource> getCurrentKeys() {
		return this.keys;
	}

	@Override
	public String toString() {
		return "GraphAgent{" +
				"keys=" + keys +
				", graphs=" + graphs +
				'}';
	}

	/*

	export const createBlankNodeWithChildPost = (
	  parentIndex: string = '',
	  childIndex: string = '',
	  contents: Content[]
	) => {
	  const date = unixToDa(Date.now()).toString();
	  const nodeIndex = parentIndex + '/' + date;

	  const childGraph = {};
	  childGraph[childIndex] = {
		post: {
		  author: `~${window.ship}`,
		  index: nodeIndex + '/' + childIndex,
		  'time-sent': Date.now(),
		  contents,
		  hash: null,
		  signatures: []
		},
		children: null
	  };

	  return {
		post: {
		  author: `~${window.ship}`,
		  index: nodeIndex,
		  'time-sent': Date.now(),
		  contents: [],
		  hash: null,
		  signatures: []
		},
		children: childGraph
	  };
	};
		 */
	// thought: the original api calls Date.now() multiple times instead of using a single value. is this behavior preferred or necessary?
	public static Node createBlankNodeWithChildPost(String shipAuthor, String parentIndex, String childIndex, List<GraphContent> contents) {
		parentIndex = requireNonNullElse(parentIndex, "");
		childIndex = requireNonNullElse(childIndex, "");

		final var date = AirlockUtils.unixToDa(Instant.now().toEpochMilli()).toString();
		final var nodeIndex = parentIndex + '/' + date;

		List<BigInteger> parsedIndexArray = Graph.indexListFromString(childIndex);
		if (parsedIndexArray.size() != 1) {
			// see if we want to keep  this or not
			throw new IllegalArgumentException("invalid index provided");
		}
		BigInteger index = parsedIndexArray.get(0);

		Graph childGraph = new Graph(Map.of(index, new Node(
				new Post(
						ShipName.withSig(shipAuthor),
						nodeIndex + '/' + childIndex,
						Instant.now().toEpochMilli(),
						contents,
						null,
						Collections.emptyList()
				),
				Graph.EMPTY_GRAPH
		)));

		return new Node(
				new Post(
						ShipName.withSig(shipAuthor),
						nodeIndex,
						Instant.now().toEpochMilli(),
						Collections.emptyList(),
						null,
						Collections.emptyList()
				),
				childGraph
		);
	}


	/*
	* function markPending(nodes: any) {
  _.forEach(nodes, node => {
    node.post.author = deSig(node.post.author);
    node.post.pending = true;
    markPending(node.children || {});
  });
}
*/

/*

export const createPost = (
  contents: Content[],
  parentIndex: string = '',
  childIndex:string = 'DATE_PLACEHOLDER'
) => {
  if (childIndex === 'DATE_PLACEHOLDER') {
    childIndex = unixToDa(Date.now()).toString();
  }
  return {
    author: `~${window.ship}`,
    index: parentIndex + '/' + childIndex,
    'time-sent': Date.now(),
    contents,
    hash: null,
    signatures: []
  };
};
*/

	public static Post createPost(String shipAuthor, List<GraphContent> contents, @Nullable String parentIndex, @Nullable String childIndex) {
		// todo make this api design more idiomatic by adding overloaded method for nullable params

		parentIndex = requireNonNullElse(parentIndex, "");
		childIndex = requireNonNullElse(childIndex, "DATE_PLACEHOLDER");
		if (childIndex.equals("DATE_PLACEHOLDER")) {
			childIndex = AirlockUtils.unixToDa(Instant.now().toEpochMilli()).toString();
		}

		return new Post(
				shipAuthor,
				parentIndex + "/" + childIndex,
				Instant.now().toEpochMilli(),
				contents,
				null,
				Collections.emptyList()
		);

	}

	public static Post createPost(String shipAuthor, List<GraphContent> contents) {
		return GraphAgent.createPost(shipAuthor, contents, null, null);
	}



	/*
	function moduleToMark(mod: string): string | undefined {
	  if(mod === 'link') {
		return 'graph-validator-link';
	  }
	  if(mod === 'publish') {
		return 'graph-validator-publish';
	  }
	  if(mod === 'chat') {
		return 'graph-validator-chat';
	  }
	  return undefined;
	}
	*/
	public enum Module {
		LINK("graph-validator-link"),
		PUBLISH("graph-validator-publish"),
		CHAT("graph-validator-chat");

		public final String mark;

		Module(String mark) {
			this.mark = mark;
		}

		public String moduleName() {
			return this.name().toLowerCase();
		}

	}

	static String moduleToMark(Module module) {
		return module.mark;
	}

	/*

  private storeAction(action: any): Promise<any> {
    return this.action('graph-store', 'graph-update', action)
  }
  */
	private CompletableFuture<PokeResponse> storeAction(JsonObject payload) throws AirlockResponseError, AirlockRequestError, AirlockAuthenticationError {
		return this.action("graph-store", "graph-update", payload, null);
	}

	/*
	  private viewAction(threadName: string, action: any) {
		return this.spider('graph-view-action', 'json', threadName, action);
	  }
	*/
	private JsonElement viewAction(String threadName, JsonObject payload) throws AirlockResponseError, AirlockRequestError, SpiderFailureException, AirlockAuthenticationError {
		return this.urbit.spiderRequest("graph-view-action", threadName, "json", payload);
	}

	/*
  private hookAction(ship: Patp, action: any): Promise<any> {
    return this.action('graph-push-hook', 'graph-update', action);
  }

*/
	private CompletableFuture<PokeResponse> hookAction(String ship, JsonObject payload) throws AirlockResponseError, AirlockRequestError, AirlockAuthenticationError {
		// okay i don't know if you actually need ship or not based simply on porting right now
		// so i guess todo to test out ship unused parameter behaviour
		return this.action("graph-push-hook", "graph-update", payload, ship);
	}


	/*
  createManagedGraph(
    name: string,
    title: string,
    description: string,
    group: Path,
    mod: string
  ) {
    const associated = { group: resourceFromPath(group) };
    const resource = makeResource(`~${window.ship}`, name);

    return this.viewAction('graph-create', {
      "create": {
        resource,
        title,
        description,
        associated,
        "module": mod,
        mark: moduleToMark(mod)
      }
    });
  }
  */
	public JsonElement createManagedGraph(String name, String title, String description, String pathOfGroup, Module module) throws SpiderFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		return this.createManagedGraph(name, title, description, GroupUtils.resourceFromPath(pathOfGroup), module);
	}


	public JsonElement createManagedGraph(String name, String title, String description, Resource groupResource, Module module) throws SpiderFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		// todo note: name should be url safe. also, landscape tends to replace spaces and other characters with dashes, but we aren't doing that yet
		final var associated = Map.of("group", groupResource);
		final var resource = GroupUtils.makeResource(ShipName.withSig(this.urbit.getShipName()), name);

		JsonObject createPayload = gson.toJsonTree(Map.of(
				"create", Map.of(
						"resource", resource,
						"title", title,
						"description", description,
						"associated", associated,
						"module", module.moduleName(),
						"mark", moduleToMark(module)
				)
		)).getAsJsonObject();

		return this.viewAction("graph-create", createPayload);
	}

	/*
  createUnmanagedGraph(
    name: string,
    title: string,
    description: string,
    policy: Enc<GroupPolicy>,
    mod: string
  ) {
    const resource = makeResource(`~${window.ship}`, name);

    return this.viewAction('graph-create', {
      "create": {
        resource,
        title,
        description,
        associated: { policy },
        "module": mod,
        mark: moduleToMark(mod)
      }
    });
  }
*/


	public JsonElement createUnmanagedGraph(String name, String title, String description, GroupPolicy policy, Module module) throws AirlockResponseError, AirlockRequestError, SpiderFailureException, AirlockAuthenticationError {
		/*
		This requires Enc<S>, which idk wtf it's doing. I mean i guess i get it now but like translating this requires a different way of doing things compeltely.
		I think i can just implement the equivalent in a custom seralizer or a utlitiy class...

		// Turns sets into arrays and maps into objects so we can send them over the wire
		export type Enc<S> =
		S extends Set<any> ?
		Enc<SetElement<S>>[] :
		S extends Map<string, any> ?
		{ [s: string]: Enc<MapValue<S>> } :
		S extends object ?
				{ [K in keyof S]: Enc<S[K]> } :
		S;

		 */
		final var resource = GroupUtils.makeResource(urbit.getShipName(), name);
		return this.viewAction("graph-create", map2json(Map.of(
				"create", Map.of(
						"resource", resource,
						"title", title,
						"description", description,
						"associated", Map.of("policy", policy),
						"module", module.moduleName(),
						"mark", moduleToMark(module)
				)
		)));

	}


	/*
  joinGraph(ship: Patp, name: string) {
    const resource = makeResource(ship, name);
    return this.viewAction('graph-join', {
      join: {
        resource,
        ship,
      }
    });
  }
*/
	public JsonElement joinGraph(String ship, String name) throws SpiderFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		final var resource = GroupUtils.makeResource(ship, name);
		return this.viewAction("graph-join", map2json(Map.of(
				"join", Map.of(
						"resource", resource,
						"ship", ship
				)
		)));
	}

	/*
  deleteGraph(name: string) {
    const resource = makeResource(`~${window.ship}`, name);
    return this.viewAction('graph-delete', {
      "delete": {
        resource
      }
    });
  }
*/
	public JsonElement deleteGraph(String name) throws SpiderFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		final var resource = GroupUtils.makeResource(this.urbit.getShipName(), name);
		return this.viewAction("graph-delete", map2json(Map.of(
				"delete", resource
		)));
	}


	/*
  leaveGraph(ship: Patp, name: string) {
    const resource = makeResource(ship, name);
    return this.viewAction('graph-leave', {
      "leave": {
        resource
      }
    });
  }

*/
	public JsonElement leaveGraph(String ship, String name) throws SpiderFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		final var resource = GroupUtils.makeResource(this.urbit.getShipName(), name);
		return this.viewAction("graph-leave", map2json(Map.of("leave", resource)));
	}

	/*

  groupifyGraph(ship: Patp, name: string, toPath?: string) {
    const resource = makeResource(ship, name);
    const to = toPath && resourceFromPath(toPath);

    return this.viewAction('graph-groupify', {
      groupify: {
        resource,
        to
      }
    });
  }

*/
	public JsonElement groupifyGraph(String ship, String name, String toPath) throws SpiderFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		final var resource = GroupUtils.makeResource(this.urbit.getShipName(), name);

		final Resource to = GroupUtils.resourceFromPath(toPath);
		return this.viewAction("graph-groupify", map2json(Map.of(
				"groupify", Map.of(
						"resource", resource,
						"to", to
				)
		)));
	}

	public JsonElement groupifyGraph(String ship, String name) throws SpiderFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		return this.groupifyGraph(ship, name, null);
	}

	/*

  addGraph(ship: Patp, name: string, graph: any, mark: any) {
    return this.storeAction({
      'add-graph': {
        resource: { ship, name },
        graph,
        mark
      }
    });
  }
  */
	public CompletableFuture<PokeResponse> addGraph(String ship, String name, Map<String, Object> graph, String mark) throws AirlockResponseError, AirlockRequestError, AirlockAuthenticationError {
		return this.storeAction(map2json(Map.of(
				"add-graph", Map.of(
						"resource", new Resource(ship, name),
						"graph", graph,
						"mark", mark
				)
		)));
	}


	/*


  addPost(ship: Patp, name: string, post: Post) {
    let nodes = {};
    nodes[post.index] = {
      post,
      children: null
    };
    return this.addNodes(ship, name, nodes);
  }
  */
	public CompletableFuture<PokeResponse> addPost(String ship, String name, Post post) throws AirlockResponseError, AirlockRequestError, AirlockAuthenticationError {

		return this.addNodes(ship, name, Map.of(
				post.index, new Node(post, Graph.EMPTY_GRAPH)
		));

	}


	/*



  addNode(ship: Patp, name: string, node: Object) {
    let nodes = {};
    nodes[node.post.index] = node;

    return this.addNodes(ship, name, nodes);
  }
*/
	public CompletableFuture<PokeResponse> addNode(String ship, String name, Node node) throws AirlockResponseError, AirlockRequestError, AirlockAuthenticationError {
		Map<String, Object> nodes = new HashMap<>();

		nodes.put(node.post.index, node);

		return this.addNodes(ship, name, nodes);

	}

	/*
  addNodes(ship: Patp, name: string, nodes: Object) {
    const action = {
      'add-nodes': {
        resource: { ship, name },
        nodes
      }
    };

    const promise = this.hookAction(ship, action);
    markPending(action['add-nodes'].nodes);
    action['add-nodes'].resource.ship = action['add-nodes'].resource.ship.slice(1);
    console.log(action);
    this.store.handleEvent({ data: { 'graph-update': action } });
    return promise;
  }
*/

	/**
	 *
	 * @param ship The ship where the resource (graph) is located
	 * @param name The name of the resource
	 * @param nodes The nodes to add
	 * @return A future poke response
	 * @throws AirlockResponseError
	 * @throws AirlockRequestError
	 * @throws AirlockAuthenticationError
	 */
	public CompletableFuture<PokeResponse> addNodes(String ship, String name, Map<String, Object> nodes) throws AirlockResponseError, AirlockRequestError, AirlockAuthenticationError {
		final var payload = map2json(Map.of(
				"add-nodes", Map.of(
						"resource", new Resource(ship, name),
						"nodes", nodes
				)
		));

		CompletableFuture<PokeResponse> future = this.hookAction(ship, payload);

		/*
		markPending(action['add-nodes'].nodes);
		action['add-nodes'].resource.ship = action['add-nodes'].resource.ship.slice(1);
		console.log(action);

		this.store.handleEvent({ data: { 'graph-update': action } });
		*/
		this.updateState(payload); // we are consuming our own update in this case

		return future;
	}

	/*


  removeNodes(ship: Patp, name: string, indices: string[]) {
    return this.hookAction(ship, {
      'remove-nodes': {
        resource: { ship, name },
        indices
      }
    });
  }
*/
	public CompletableFuture<PokeResponse> removeNodes(String ship, String name, String[] indices) throws AirlockResponseError, AirlockRequestError, AirlockAuthenticationError {
		return this.hookAction(ship, map2json(Map.of(
				"remove-nodes", Map.of(
						"resource", new Resource(ship, name),
						"indices", indices
				)
		)));
	}

	/*


  getKeys() {
    return this.scry<any>('graph-store', '/keys')
      .then((keys) => {
        this.store.handleEvent({
          data: keys
        });
      });
  }

*/
	public JsonObject getKeys() throws ScryDataNotFoundException, ScryFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		/*
		const keys = (json, state) => {
		  const data = _.get(json, 'keys', false);
		  if (data) {
		    state.graphKeys = new Set(data.map((res) => {
		      let resource = res.ship + '/' + res.name;
		      return resource;
		    }));
		  }
		};
		 */

		JsonObject scryResponse = this.urbit.scryRequest("graph-store", "/keys").getAsJsonObject();
		// this should be an object of the form:
		// {"graph-update": "keys": [{"name: "test", "ship": "zod"}, ...]}
		JsonObject graphUpdate = scryResponse.get("graph-update").getAsJsonObject();

		this.updateState(graphUpdate);

		return scryResponse;
	}


	/*

  getTags() {
    return this.scry<any>('graph-store', '/tags')
      .then((tags) => {
        this.store.handleEvent({
          data: tags
        });
      });
  }

*/

	public JsonElement getTags() throws ScryFailureException, ScryDataNotFoundException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		// old_todo state handling
		// so apparently, Landscape doesn't do anything with tags yet... we'll also not do anything
		// there seems to be a similar concept used with group-store tho


		JsonElement scryResponse = this.urbit.scryRequest("graph-store", "/tags");
		this.updateState(scryResponse.getAsJsonObject().getAsJsonObject("graph-update"));
		return scryResponse;
	}

	/*

  getTagQueries() {
    return this.scry<any>('graph-store', '/tag-queries')
      .then((tagQueries) => {
        this.store.handleEvent({
          data: tagQueries
        });
      });
  }

*/
	public JsonElement getTagQueries() throws ScryFailureException, ScryDataNotFoundException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		// Landscape doesn't do anything with tagQueries yet either

		JsonElement scryResponse = this.urbit.scryRequest("graph-store", "/tag-queries");
		return scryResponse;
	}

	/*

  getGraph(ship: string, resource: string) {
    return this.scry<any>('graph-store', `/graph/${ship}/${resource}`)
      .then((graph) => {
        this.store.handleEvent({
          data: graph
        });
      });
  }
*/
	public JsonElement getGraph(String ship, String resourceName) throws ScryDataNotFoundException, ScryFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		JsonElement scryResponse = this.urbit.scryRequest("graph-store", "/graph/" + ship + "/" + resourceName);
		this.updateState(scryResponse.getAsJsonObject().getAsJsonObject("graph-update"));
		return scryResponse;
	}

	/*
  async getNewest(ship: string, resource: string, count: number, index = '') {
    const data = await this.scry<any>('graph-store', `/newest/${ship}/${resource}/${count}${index}`);
    this.store.handleEvent({ data });
  }
	*/

	public void getNewest(Resource resource, int count) throws ScryDataNotFoundException, ScryFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		getNewest(resource, count, "");
	}

	public void getNewest(Resource resource, int count, String index) throws ScryDataNotFoundException, ScryFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		final var data = this.urbit.scryRequest("graph-store", "/newest/" + resource.urlForm() + "/" + count + index);
		this.updateState(data.getAsJsonObject().getAsJsonObject("graph-update"));
		// thing to do: look at example payload and how it is used
		// there is only one usage, which is here: https://github.com/urbit/urbit/blob/master/pkg/interface/src/views/apps/chat/ChatResource.tsx#L42
	}
	
	

	/*

  async getOlderSiblings(ship: string, resource: string, count: number, index = '') {
    const idx = index.split('/').map(decToUd).join('/');
    const data = await this.scry<any>('graph-store',
       `/node-siblings/older/${ship}/${resource}/${count}${idx}`
     );
    this.store.handleEvent({ data });
  }
*/
	public void getOlderSiblings(String ship, String resource, int count, String index) throws ScryDataNotFoundException, ScryFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		final var idx = Arrays.stream(index.split("/")).map(AirlockUtils::decToUd).collect(Collectors.joining("/"));
		final var data = this.urbit.scryRequest("graph-store", "/node-siblings/older/" + ship + "/" + resource + "/" + count + idx);
		this.updateState(data.getAsJsonObject().getAsJsonObject("graph-update"));
	}

	/*


  async getYoungerSiblings(ship: string, resource: string, count: number, index = '') {
    const idx = index.split('/').map(decToUd).join('/');
    const data = await this.scry<any>('graph-store',
       `/node-siblings/younger/${ship}/${resource}/${count}${idx}`
     );
    this.store.handleEvent({ data });
  }
	*/

	public void getYoungerSiblings(String ship, String resource, int count, String index) throws ScryDataNotFoundException, ScryFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		final var idx = Arrays.stream(index.split("/")).map(AirlockUtils::decToUd).collect(Collectors.joining("/"));
		final var data = this.urbit.scryRequest("graph-store", "/node-siblings/younger/" + ship + "/" + resource + "/" + count + idx);
		this.updateState(data.getAsJsonObject().getAsJsonObject("graph-update"));
	}

	/*

  getGraphSubset(ship: string, resource: string, start: string, end: string) {
    return this.scry<any>(
      'graph-store',
      `/graph-subset/${ship}/${resource}/${end}/${start}`
    ).then((subset) => {
      this.store.handleEvent({
        data: subset
      });
    });
  }
*/
	public JsonElement getGraphSubset(String ship, String resource, String start, String end) throws ScryDataNotFoundException, ScryFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		JsonElement scryResponse = this.urbit.scryRequest("graph-store", "/graph-subset/" + ship + "/" + resource + "/" + end + "/" + "start");
		this.updateState(scryResponse.getAsJsonObject().getAsJsonObject("graph-update"));
		return scryResponse;
	}


	/*


  getNode(ship: string, resource: string, index: string) {
    const idx = index.split('/').map(numToUd).join('/');
    return this.scry<any>(
      'graph-store',
      `/node/${ship}/${resource}${idx}`
    ).then((node) => {
      this.store.handleEvent({
        data: node
      });
    });
  }
}
*/
	public JsonElement getNode(String ship, String resource, String index) throws ScryDataNotFoundException, ScryFailureException, AirlockAuthenticationError, AirlockResponseError, AirlockRequestError {
		// todo unpack the following mystery meat code for idx
		//  it is unclear to me what the inputs and outputs to this are, reading it like 2 days later
		// it would be best to see what the source is actually doing and replicate that rather than just blindly translating
		final var idx = Arrays.stream(index.split("/")).map(AirlockUtils::decToUd).collect(Collectors.joining("/"));
		// i think this is because index can be something like 1 turns to 2
		// source (unix timestamp): 16555555555/2
		// processed (@da timestamp): /17055555555555555555555555555555/2
		//
		final var data = this.urbit.scryRequest("graph-store", "/node-siblings/younger/" + ship + "/" + resource + "/" + idx);
		this.updateState(data.getAsJsonObject().getAsJsonObject("graph-update"));
		return data;
	}


	// could also be called `reduce`
	private void updateState(@NotNull JsonObject graphUpdate) {
		// expects the object associated with the key "graph-update"
		if (graphUpdate.has("keys")) {
			JsonArray keys = graphUpdate.get("keys").getAsJsonArray();
			this.keys.clear();
			/*
			 * stream(keys) => for each key:
			 * map(keyObj -> make Resource.class)
			 * then, collect to a single list
			 * then, set this.keys to that list
			 */
			this.keys.addAll(
					stream(keys.spliterator(), false)
							.map(resourceJSON -> AirlockUtils.gson.fromJson(resourceJSON, Resource.class))
							.collect(Collectors.toSet())
			);
		} else if (graphUpdate.has("add-graph")) {

			JsonObject addGraph = graphUpdate.get("add-graph").getAsJsonObject();
			Resource resource = gson.fromJson(addGraph.get("resource"), Resource.class);
			Graph newGraph = gson.fromJson(addGraph.get("graph"), Graph.class);

			Graph processedNewGraph = new Graph();


			// it seems like all it does is ensure all childrenNodes have at least a nonnull
			// `children` property that is init with an empty graph
			newGraph.forEach((index, node) -> {
				node.ensureAllChildrenHaveGraph();
				processedNewGraph.put(index, node);
			});

			this.graphs.put(resource, processedNewGraph);
			this.keys.add(resource);

		} else if (graphUpdate.has("remove-graph")) {

			JsonObject removeGraphObj = graphUpdate.get("remove-graph").getAsJsonObject();
			Resource resource = gson.fromJson(removeGraphObj, Resource.class);

			if (!this.graphs.containsKey(resource)) {
				System.out.println("Warning: Tried to remove non-existent graph");
			} else {
				this.keys.remove(resource);
				this.graphs.remove(resource);
			}

		} else if (graphUpdate.has("add-nodes")) {

			JsonObject addNodesObj = graphUpdate.getAsJsonObject("add-nodes");
			JsonObject nodesObj = addNodesObj.getAsJsonObject("nodes");
			Resource resource = gson.fromJson(addNodesObj.getAsJsonObject("resource"), Resource.class);

			// todo implement _addNodes. that method is kinda weird

			if (this.graphs.isEmpty()) {
				return;
			}

			if (!this.graphs.containsKey(resource)) {
				this.graphs.put(resource, new Graph());
			}

			Graph targetGraph = this.graphs.get(resource);
			this.keys.add(resource);

			nodesObj.entrySet().forEach(indexNodeEntry -> {
				// note: landscape is more flexible in regards to malformed index strings.
				// i.e., if an index doesn't parse it just stops parsing further entries
				// and returns without fanfare.
				// however, the behavior here is to just completely halt execution and throw an exception
				// when the BigInt parsing inevitably fails
				DeepIndex deepIndex = Graph.indexListFromString(indexNodeEntry.getKey());
				if (deepIndex.size() == 0) {
					return;
				}
				Node node = gson.fromJson(indexNodeEntry.getValue().getAsJsonObject(), Node.class);
				this.graphs.get(resource).addNode(deepIndex, node);
			});
		} else if (graphUpdate.has("remove-nodes")) {
			// indices != index.
			// index = "/1767324682374638723487987324"
			// indices = "/1767324682374638723487987324/1/4"
			// since index is a List<BigInteger>,
			// `indices` would be List<List<BigInteger>>
			// List{BitInt(1767324682374638723487987324), BigInt(1), BigInt(4)}
			JsonObject removeNodesObj = graphUpdate.getAsJsonObject("remove-nodes");
			JsonArray indicesObj = removeNodesObj.getAsJsonArray("indices");
			List<DeepIndex> indices =
					stream(indicesObj.spliterator(), false)
							.map(indexObj -> Graph.indexListFromString(indexObj.getAsString()))
							.collect(Collectors.toList());


			Resource resource = gson.fromJson(removeNodesObj.getAsJsonObject("resource"), Resource.class);
			if (!this.graphs.containsKey(resource)) {
				return;
			}

			for (DeepIndex index : indices) {
				if (index.isEmpty()) {
					System.out.println("Warning, encountered empty index: " + index);
					return;
				}
				this.graphs.get(resource).removeNode(index);
			}

		} else {
			System.out.println("Warning: encountered unknown graph-update payload. Ignoring");
		}
		// no further code should be written here because it would be skipped by early exits
	}

}
