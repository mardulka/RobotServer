# Robot Server

Code represents server application providing connection for robot clients - each robot has own thread. Whole code is in single file because of rules for final task submiting.

Robots need to authenticate first with defined server-client key pairs which are verified with defined hashing logic. Robots starts in random position with random orientation in 2D field filled with obstacles. They are supposed to get on position [0,0] to pick up secret message. After getting target messages, robots fullfilled they purpose, they log out and connection is closed.

