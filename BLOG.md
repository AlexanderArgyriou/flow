# Experimenting with Quarkus flow

## Designing a Purchase Approval Workflow

If you've ever tried to build an orchestration engine by hand, 
the kind of thing where a purchase order needs to go through validation, 
wait for a human to approve it, then either get rejected or move on to fulfillment, 
you know how quickly it turns into a mess of status columns, 
cron jobs polling for state changes, and `if` statements nested three levels deep. 
I've built that mess more than once, both from 
scratch(ashamed to admit that) or using external runtimes like temporal. 
This time I wanted to try something different that caught my eye recently: [Quarkus Flow](https://docs.quarkiverse.io/quarkus-flow/dev/index.html), 
a Quarkus extension that implements the [Open Workflow Specification](https://open-workflow-specification.org/).

The idea is simple. 
Describe your business process as a workflow of tasks, 
let the engine run it, persist its state, 
and let it pause and resume whenever it needs to wait for something external, 
like a Kafka message or a human decision (Actually it can do a lot more, but that's enough for our example).
What I didn't expect was how little burden it takes to get all of that working 
in a real Quarkus app, and how much of it reuses Quarkus concepts I already knew. 
This post walks through a small purchasing demo I built to experiment with it,
and along the way I'll point out the parts of both Quarkus Flow and plain Quarkus that make this
pleasant.

## A simplified scenario

A purchase request comes in through a REST endpoint. 
It needs to be validated, have stock reserved against it,
then it sits and waits for a human to approve or reject it. 
Depending on that decision, it either gets marked rejected or continues
on to create a supplier order and complete. Nothing exotic, 
this is the shape of half the business processes in any company's backlog.
What makes it interesting from an engineering point of view is the "wait for approval" step.
The workflow has to pause, potentially for hours or days, 
and then continue exactly where it left off when an approval event shows up. 
That's the part that usually causes pain, and it's exactly the part Quarkus Flow is built to handle.

## Workflows are just Java classes (p.s. yaml option is there too)

A `Flow` is a plain CDI bean, you extend `Flow` and return a `Workflow` descriptor 
from `descriptor()`. At build time, the extension discovers every `Flow` subclass 
on the classpath and registers it as a CDI bean, the same build time discovery mechanism Quarkus uses for 
REST resources or Panache entities. That matters for two reasons: startup is fast because
nothing about the workflow's shape needs to be figured out at runtime.
Here's the purchase flow from this project, trimmed slightly: 

```java
@ApplicationScoped
public class PurchaseFlow extends Flow {

    private final PurchaseService purchaseService;

    public PurchaseFlow(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    @Override
    public Workflow descriptor() {
        return FlowWorkflowBuilder.workflow("purchase").tasks(
            function("createPurchase", this::createPurchase),
            function("validatePurchase", this::validatePurchase),
            function("reserveInventory", this::reserveInventory),
            function("waitingApproval", this::waitingApproval),
            listen("waitForApproval", toOne(
                consumed("org.flow.approval")
                    .dataAs(ApprovalEvent.class, (event, wfCtx, taskCtx) -> {
                        Purchase current = taskCtx.input().as(Purchase.class).orElseThrow();
                        return event.purchaseId().equals(current.id);
                    })
            )),
            switchWhenOrElse(
                (ApprovalEvent event) -> ApprovalStatus.APPROVED.equals(event.decision()),
                "approved", "rejected", ApprovalEvent.class
            ),
            function("rejected", this::rejected),
            function("approved", this::approved),
            function("createSupplierOrder", this::createSupplierOrder),
            function("completePurchase", this::complete)
        ).build();
    }
    // ...task method bodies below
}
```

That's the whole workflow. No state machine library, 
no separate orchestration server to deploy. 
Each `function(...)` step is just a plain Java method reference.The `listen(...)` 
step is the one doing the heavy lifting. 
It tells the engine to pause here and resume only when a matching event 
arrives on the `org.flow.approval` channel, 
filtered by a predicate that checks the purchase ID. 
`toOne` is one of the correlation shapes the spec supports, for when a task needs to wait on one event. `switchWhenOrElse` is a conditional branch, 
exactly like an `if/else`, 
expressed declaratively as part of the workflow graph, 
and it can be driven either by a typed Java predicate like here or by a jq expression string.

What I like here is that this reads like a table of contents 
for the business process. Anyone, even someone who doesn't write Java, 
could look at that task list and understand the purchasing flow. 

## Starting a flow instance from REST

Kicking off a new workflow instance is just calling `startInstance`. 
From a JAX-RS resource perspective could be something like:

```java
@POST
public Uni<Map<String, Object>> create(CreatePurchaseRequest request) {
    return purchaseFlow.startInstance(request)
            .map(r -> r.asMap().orElseThrow());
}
```

That's it. `startInstance` runs the workflow up to the point where it either 
finishes or hits a `listen` step and parks itself. 
Notice this returns a `Uni`, Quarkus Flow is reactive by default, 
which fits naturally with Quarkus's reactive stack.

## Persisting flow state with JPA

This is the part that would normally take days to build correctly: 
what happens when your app restarts while a workflow is sitting mid flight, 
waiting for an approval? 
By default, Quarkus Flow runs entirely in memory, 
which is fast but means a crash, restart, or scale down loses every in flight instance. 
The persistence extension changes that by writing the workflow's state to a durable store every time 
a task completes or pauses.
Adding it is a one line dependency:

```xml
<dependency>
    <groupId>io.quarkiverse.flow</groupId>
    <artifactId>quarkus-flow-jpa</artifactId>
</dependency>
```

Combine that with the regular `quarkus-hibernate-orm-panache` and a PostgreSQL datasource, 
and Flow checkpoints each workflow instance's progress into the database as it moves through tasks. 
According to the docs, this is exactly what lets workflows survive three real world situations: 
pausing for days or weeks on an asynchronous callback such as a human approval or a webhook, 
recovering automatically after an unexpected JVM crash, 
and being safely migrated across pods during a Kubernetes rolling update or node drain. 
In this project, if the JVM crashes right after `reserveInventory` runs but before the approval 
event shows up, the instance's position in the graph and its data are safely stored.
When the matching Kafka event eventually arrives, the engine resumes from exactly where it paused,
no manual "where was I" bookkeeping required.

This pairs nicely with your own domain persistence. In this project, `Purchase` is a normal Panache entity, persisted by `PurchaseService` inside `@Transactional` methods, completely independent of the workflow engine's own persisted state. The workflow orchestrates, your entities hold your actual business data. Two separate, clean concerns, both using the same familiar Hibernate ORM you already know from any other Quarkus app.

## Emitting and consuming events

The other half of the puzzle is how a workflow talks to the outside world, 
and how the outside world talks back. Quarkus Flow's messaging extension bridges workflows 
to Kafka using Quarkus's existing reactive messaging stack.

Emitting an event from a workflow is a single DSL step. 
The `ApprovalFlow` in this project is a tiny, 
separate workflow whose only job is to turn an HTTP approval request into an event on the bus:

```java
@Override
public Workflow descriptor() {
    return FlowWorkflowBuilder.workflow("approval").tasks(
        function("buildApproval", this::buildApproval),
        emitJson("org.flow.approval", ApprovalEvent.class)
    ).build();
}
```

A "manager" hits `POST /approval` with a decision, 
this flow builds an `ApprovalEvent` record and publishes it as a cloud event of type
`org.flow.approval`. Meanwhile, over in `PurchaseFlow`, 
the `listen(...)` step showed earlier is subscribed to that exact same event type, 
filtering for the one purchase it cares about. The two workflows never call each other directly,
they're decoupled entirely through events, exactly the way you'd want a real distributed system to behave.

Under the hood this all runs against Kafka, but note how little Kafka specific 
code you actually write. 
You declare `emitJson` on one side and `consumed(...).dataAs(...)` on the other, 
and Quarkus Flow handles wiring it through Reactive Messaging's Kafka connector. 
For local development, `quarkus.flow.messaging.devservices-messaging-enabled=true`
handles all the boilerplate config for you.

## Testing the flow end to end

Reading the DSL is one thing, watching a thousand workflow instances actually pause and resume is another, 
so I vibe-wrote (to be honest) a small `test.sh` script to hammer 
the app and see how it behaves under load rather than just testing one purchase by hand.

```bash
#!/usr/bin/env bash

BASE_URL="http://localhost:9695"

echo "Firing 1000 purchase requests..."

for i in $(seq 1 1000); do
  curl -s -X POST "$BASE_URL/purchases" \
    -H "Content-Type: application/json" \
    -d "{
      \"requester\": \"john$i\",
      \"description\": \"MacBook Pro\",
      \"supplier\": \"Apple\",
      \"total\": 2499.99
    }" > /dev/null &

  echo "Fired purchase $i/1000"
done

echo "All 1000 purchase requests fired."
echo "Waiting 2 minutes..."

sleep 120

echo "Firing 1000 approval requests..."

for i in $(seq 1 1000); do
  if (( RANDOM % 2 == 0 )); then
    decision="APPROVED"
  else
    decision="REJECTED"
  fi

  curl -s -X POST "$BASE_URL/approval" \
    -H "Content-Type: application/json" \
    -d "{
      \"purchaseId\": $i,
      \"decision\": \"$decision\"
    }" > /dev/null &

  echo "Fired approval $i/1000: $decision"
done

echo "All 1000 approval requests fired."
```

You can find the full project here: [flow-project](https://github.com/AlexanderArgyriou/flow)

Before running it, spin up the infrastructure with `docker-compose up -d` 
for Postgres, then start the app in dev mode with `./mvnw quarkus:dev`. 
Once the app is up on port 9695, run `./test.sh`.

The script fires one thousand purchase requests in parallel, each one hitting 
`POST /purchases` and starting a brand new instance of `PurchaseFlow`. 
Every one of those instances runs through `createPurchase`, `validatePurchase` and `reserveInventory`, 
then parks itself on the `waitForApproval` listen step, 
sitting in Postgres thanks to the JPA persistence extension rather than just in memory. 
That two minute sleep in the middle is deliberate, 
it gives you a window to go look at the database while every instance is genuinely paused, 
waiting on an event that hasn't arrived yet. 
After the sleep, the script fires a thousand approval requests, 
each one a `POST /approval` that runs the tiny `ApprovalFlow`, builds an `ApprovalEvent`, 
and emits it onto Kafka. That's the moment where all one thousand paused `PurchaseFlow` 
instances should wake up, each one matching only the approval event carrying its own purchase id, 
then racing through `approved` or `rejected` and on to completion.

To check the results, the simplest option is the `GET /purchases/{id}` endpoint already in the project:

```bash
curl -s http://localhost:9695/purchases/42 | jq
```

That returns the current state of a single `Purchase` entity, including its `status`, so you can confirm it landed on `COMPLETED` or `REJECTED` rather than getting stuck on `WAITING_APPROVAL`. For a broader view across all one thousand purchases, it's easier to just query Postgres directly:

```bash
docker exec -it purchasing-postgres psql -U purchasing -d purchasing \
  -c "SELECT status, count(*) FROM purchases GROUP BY status;"
```

If everything worked, you should see roughly half the rows sitting in `COMPLETED` and half in `REJECTED`, 
matching the random coin flip in the script, and nothing left behind in `WAITING_APPROVAL` or 
`STOCK_RESERVED`.

## Why this combination works so well imho

Stepping back, what struck me building this demo wasn't any single feature, 
it was how well Quarkus Flow fits into the existing Quarkus ecosystem 
instead of bolting a foreign framework on top of it.
The other win is the separation between orchestration logic and business logic inside the same app.