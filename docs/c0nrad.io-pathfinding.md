I had an idea for a fun “grid search” pathfinding algorithm for MIT battlecode, so decided to write a little playground in JS. This article covers the results.

## Brainstorming Pathfinding Algorithms: QuadTree & RelaxPath

### MIT Battlecode

- Yearly competition hosted by MIT
- They give you a game spec, and some java scaffolding, you wrote code to play the game
- The game usually involves resources, bases, units, etc.
- Fog of War, limited communication between units
- As a fun constraint, there’s a JVM bytecode limit per turn, ~10,000.

### Common Algorithms

**moveTowards**: Move towards the target, if you get stuck, you get stuck. Sometimes moveRandom in hopes it helps get unstuck.**Bug Nav**: Move towards the target, if you hit a wall, mark the other side and follow the wall around.**BFS**: Breadth First Search, search in rings from the starting posistion until you find the ending position**DFS + Priority Queue**: Sort your explore queue so that you explore the nodes closest to the goal**Unrolled Bellman-Ford**: Some sort of relaxation technique, it can be bytecode optimized by representing the map as bitmaps and doing some bitwise convolution. I think one person wrote it, and a bunch of people copy the code each year**Micro**: Floor tiles may be interactive, congestion, ctf space, group up

### pathfinding codebase

- Javascript not Java, sadly means I don’t get bytecode count, but it’s not too hard to estimate complexity
- React + canvas
- https://github.com/c0nrad/pathfinding

### Grid Search

- BFS is cool, but waste a lot of time on “boring regions”
- What if we could “simplify” the map, so that we don’t waste time on boring regions?

#### QuadTree

- What is a zone?
- Needs to be fully traversable by a simple “moveTowards”.
- Treat zone like an individual node, BFS on top

- What marks a zone?
- Corners, innies and outies

- What is QuadTree?
- Geospatial indexing. Drill down to get desired resolution.


#### “GridTree”

- But we can’t actually use QuadTree… the grid changes based on split ordering. So it’s semi quadtree but we split across multiple nodes.
- This is actually a little expensive and cumbersome… neighboring zones may not be close in the tree

- Process
**Construct The GridTree**: Iterate over every node to see if it’s a corner. If so, split at that spot.**BFS the zones**: BFS from the starting zone to the ending zone**Connect Zones**: moveTowards between all the zones**(Relax)**: Apply the relaxation algorithm to smooth out the path


### RelaxPath Algorithm

- Bug’ing can sometimes do some silly stuff (funnybug map)
- Corners are also kind of silly, what if we “relaxed” the corners inwards
- Look two ahead, and potentially move the intermediate step, or replace if closer
- Works on both bugnav/gridsearch, could potentially just relax with spare bytecode cycles

## Future

- Fun project, will probably stick with BugNav + Micro + relaxation
