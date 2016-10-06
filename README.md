VIDEQ:RELS

## Overview

Social application which provides user with scoring tool for his social connections. 

## Technologies stack

Application build on top of **Playframework!** written in **Scala 2.11.8**.

## Getting started

Project uses SBT (Simple Build Tool) to automate its building process. 

To build project from sources, you need to download and install SBT binary distributive: 
http://www.scala-sbt.org/download.html

When you have SBT ready, just run these command in order to produce project binary distributive:

`> sbt dist`

Build artifact will be places at `target/universal/bnw-backend-1.0.zip`.

## Architecture

### Playframework! basics

Every Playframework! application has two possible way to organize project structure. We've chosen classic one, with
directories structured in the Ruby-on-Rails manner:
- `app`
- - `controllers` - controllers definitions
- - `models` - domain model entities
- - `services` - business logic services interfaces and implementations
- - `modules` - IoC modules definitions; here registration of injectable dependencies happens
- - `views` - view templates definitions; all templates built on top of `Twirl` engine
- `conf`
- - `application.conf` - contains application configuration options in *HOCON* format
- - `routes` - typesafe routes definition; this file is compilable to reduce chance of possible mistake in naming or 
incorrect type usage

### Services, Inversion of Control and Guice

Play! provides developer with a convenient way of implementing *Dependency Injection* pattern by integrating
**Google Guice**.

Google Guice acts like a big Abstract Factory, that encapsulates logic of object instantiation and eventual
wiring with other dependencies he declared.
 
It works like this:
- Developer creates service `TestService`
- He registers this service inside injector context (`modules.AppModule` in our case)
- Now he can create controller `TestController` annotated with `@Inject` and put `TestService` as a parameter
 of its constructor
- Due to the fact that Play! is responsible for instantiation of `TestController`, now all new instances of `TestController`
 will receive instance of `TestService` as a parameter of their constructor

### Data access layer

Implemented as a monolith DAO facade object which provides query/update methods for all domain 
entities present in system.

At the moment only a single implementation available, which stores data in-memory. But its replacement with 
any other implementation which supports persistence (Typesafe Slick, for example) is absolutely trivial process.

In its current state, it was designed to by first of all non-blocking, what you can tell by its method results type 
- `Future[X]`.

#### Domain layer

There are few core entities, that represent basic system concepts. Here they are:
- User
- UserSession
- Person
- PersonAttribute

Model `User` represents particular user internal account within VIDEQ:RELS. It can have one or more related `Person`
models. `Person` is a person (not necessarily associated with user) from some social network. It can be either profile 
associated with user, or some relation fetched for it.

`Person` is a main entity used by scoring system. 

`Person` may have some attributes, here are their list:
- `Interests`
- `Work experience`
- `Photo`
- `Relative`

#### OAuth2 and Interaction with social sites

Authentication against given social network happens in a generic way thanks to OAuth2. There are two entities, responsible for
interaction with OAuth protocol:
- `OAuth2Service`: service responsible for interaction with `OAauth2` server and fetching access token
- `OAuth2Controller`: endpoint which `OAuth2` contacts when user approves authorization request

Authentication scheme is identical for system which support `OAuth2`. Only thing we need to do, is to provide correct
endpoint addresses and credentials for each system.

When access token was successfully fetched, `OAuthController` starts user account creation, which is operation that 
consists of these steps:
- Request user profile data 
- Create user account within Data Layer
- Link user account to the person record created on the first step
- Start authorized session for this client

When it is done, system redirects user to the page with the list of this relations.

#### Schedulers and Data Fetching

We need all possible data about user and his friends in order to produce feasible ranking results. At the moment,
actually, we don't do anything complicated - we just compare list of shared friends user has in common with one
of his connections. Pontentially, we can compare interests, work experience (worked in the same company/industry/city),
movies they like, social influence (average amount of likes/shares connection has on his posts) and etc.

So, obviously, we cannot request all this information from external systems at the time user signed into the system.

For this purpose, we have two entities:
- `PeriodicScheduler`
- `SchedulerActor`

First one starts with application and just periodically starts update process of a given information.
 
Both facilities are based on Akka Framework which provides abstractions to implement actor-systems, where agents 
instead of synchronous calls (as it happens in OO-systems) they send each other messages.
 
This architecture is beneficial for many reasons. Most important reason, is that it solve problem of concurrent computations,
as all messages inside actor are gonna be processed synchronously.
 
The second reason to use actors is their support for scaling, as you are talking to actor reference and not actor by
himself. Hence, you can easily configure your system to support remote-actor references.

So, in our case we have `PeriodicScheduler` who is responsible for sending specific message to `SchedulerActor` every
5 seconds. When `SchedulerActor` receives this message, depends on its type, he starts something.

These are the list of message types, registered within `SchedulerActor`:
- `UpdateNetwork`
- `UpdateProfile`
- `UpdateWorkExperience`
- `UpdateInterests`
- `UpdateSocialActivity`

Current implementation provides support only for `UpdateNetwork` message.

#### Ranking

As I have already told, ranking at this stage are pretty dummy thing. But, it only dummy in terms of ranking 
algorithm used at the moment. Potentially, it's just a matter of fetching more information and tuning score factors
weight.

How does it works. Ranking recognises several score factors. They have some weight W. Person rank is a total weight
of all his score factors.

`ScoreFactors` as they are at the moment:
- `ShareNetwork(friendsInCommon: Int)`
- `ShareInterests(inCommon: Int)`
- `ShareWorkPlaces(placesInCommon: Int)` 
- `IsCloseRelative`
- `SizeOfNetwork(n: Int)`
- `InfluentialPerson(averageLikes: Double, averageReposts: Double)`
 
Imaging, that some person `{Claire Wattkins#13}` has some friends `{James Gibbs#14}`, `{Michael Pollmeir#15}` and
`{Matthew Stallman#5}`.  And we're trying to estimate that value that `#14` makes to Claire.

So, we are comparing their networks looking for intersections. `#14` also has some friends. It's `{Gretta Garbo#17}` 
and `{Michael Pollmeir#5}`.  

Based on these information, our `RelationshipValueEstimatorImpl` generates this score factor:
```scala
ShareNetwork(1)
```

So, we have factore to estimate. Now we need to have some estimator. In our case it is `WeightFunction`, that is
just a `PartialFunction[ScoreFactor, Double]` that for every `ScoreFactor` returns some result of `Double`.

Default weight function is:
```scala
  final val defaultWeightFunction: RelationshipValueEstimator.WeightFunction = {
    case ScoreFactor.ShareNetwork(count) => count
    case ScoreFactor.ShareInterests(count) => count * 0.15
    case _ => 0
  }
```

Means, that value of `{James Gibbs#14}` to `{Claire Wattkins#13}` is `1`.

Ideally, we want much deeper discovery and intersections. But in most cases we are limited by data providers (Facebook) 
constraints.

#### Selenium and improved way of fetching user's data

Due to strict oficial Facebook API 