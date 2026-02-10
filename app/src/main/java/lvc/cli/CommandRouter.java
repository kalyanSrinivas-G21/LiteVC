package lvc.cli;

import java.util.HashMap;
import java.util.Map;

public class CommandRouter {

    private Map<String, Command> commands = new HashMap<>();

    public void register(String name, Command command){
        commands.put(name, command);
    }

    public void route(String[] args){

        if(args.length == 0){
            System.out.println("LiteVC - A Lightweight Version Control System");
            System.out.println("Use: litevc <command>");
            return;
        }

        String commandName = args[0];

        Command command = commands.get(commandName);

        if(command == null){
            System.out.println("Unknown command: " + commandName);
            return;
        }

        String[] commandArgs = new String[args.length - 1];
        System.arraycopy(args,1,commandArgs,0,commandArgs.length);

        command.execute(commandArgs);

    }

}
