### Project name: Flight Service
### Description:
This project is a console application that can control work and save information about flights of helicopter company
### Installation
1) Pull this repo
2) Navigate to root dir
3) Build project
   ```
   mvn clean install
   ```
4) Run project
   ```
   java -jar target/flyings-1.0-SNAPSHOT.jar
   ```
### Usage
- When you start app, it gives you an auth form with login and password
- Then app will apply strategy for program. Strategy depends on user role in company
- There are some available commands. User can list them with *** /help *** command
- The app will stop when user run command *** /out ***

### Contributing
This project is made as a part of course "Technoligies of programming for mobile applications" by [Lazovik Ignat](https://github.com/gribforyou) & [Nikitenok Diana](https://github.com/duttinka)
