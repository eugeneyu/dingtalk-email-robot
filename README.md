# dingtalk-email-robot
A demo to send daily email with work plans gathered from DingTalk chats.

Steps to deploy - 
1. Rum 'mvn package'
2. Copy jar file to VM
3. Run 'nohup java -jar daily-email.jar --server.port=8080 --accesskey.id=<your key id> --accesskey.secret=<your key secret> 2>&1 |tee dailyplan.log &'
4. Create a Dingtalk robot and point the message POST URL to http://<your domain>:8080/
5. When send message in Dingtalk, make sure you @ the robot.
6. Every day around 9:10 this app will send out an email to include daily plan of all team members.
