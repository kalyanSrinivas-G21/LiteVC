package lvc.cli;

import lvc.cli.commands.*;

public class maincli {

    public static void main(String[] args) {

        CommandRouter router = new CommandRouter();

        router.register("init", new InitCommand());
        router.register("hash", new HashObjectCommand());
        router.register("add", new AddCommand());
        router.register("commit", new CommitCommand());
        router.register("status", new StatusCommand());
        router.register("log",new LogCommand());
        router.register("branch",new BranchCommand());
        router.register("checkout",new CheckoutCommand());

        router.route(args);

    }
}
