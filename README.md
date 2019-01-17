# PaxosSimulator
Is a simple simulation tool for the __Paxos__ consensus algorithm.
The tool tries to emulate an _asynchronous system_, where the distributed _nodes_ are represented by a set of `Threads`. 

The implemented Paxos algorithm is robust against: message _lost_, _duplication_, and _node breaking_. 

In addition the simulations are parameterized. Thus, the change of the simulation parameters allows to simulate different kinds of environments: high reliability, high/low duplication rate, unreliable nodes, ...

## Simulation Parameters
The simulated environment can be customized by tuning the constants defined in the `Globals` class, some of them are:
* `TIMEOUT`: maximum waiting time (ms) for a given class of messages
* `CHANNEL_DELAY`: maximum time (ms) required to send a message to a node
* `MESSAGE_LOST_RATE`: the number of message lost every 100 units
* `ELECTION_TIMEOUT`: time (ms) before performing a new election

## Execution Summary
The executions (one or more) are associated to a `Summary` that shows statistics like:
* ___% of lost messages___
* ___% of duplicated messages___
* ___% of agreements___
* ___number of rounds___

## Project Structure
- `Paxos` is the main class
- `Channel` is responsible for message exchanging
- `Node` simulates a distributed process (or machine)
- package __stats__: contains two classes used to compute the statistics
- package __utils__: contains the `Message` and `Round` definition, 
other than the debug utilities and the execution parameters (Globals).

## How to Start
The main method is located in the `Paxos` class. 
The program is quite interactive, so just follow what the console asks.
 Eventually, before executing, tune the parameters and the debug profile.