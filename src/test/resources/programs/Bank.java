import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bank {
    static class InsufficientFunds extends RuntimeException {
        InsufficientFunds(String msg) {
            super(msg);
        }
    }

    static abstract class Account {
        private static int nextId = 1000;
        final int id;
        final String owner;
        protected int balance;
        final List<String> log = new ArrayList<>();

        Account(String owner, int initial) {
            this.id = nextId++;
            this.owner = owner;
            this.balance = initial;
            log.add("open " + initial);
        }

        void deposit(int amount) {
            balance += amount;
            log.add("deposit " + amount);
        }

        void withdraw(int amount) {
            if (amount > limit()) {
                throw new InsufficientFunds(owner + " cannot withdraw " + amount);
            }
            balance -= amount;
            log.add("withdraw " + amount);
        }

        abstract int limit();

        abstract void monthEnd();

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" + id + ", " + owner + ", " + balance + "]";
        }
    }

    static class Checking extends Account {
        Checking(String owner, int initial) {
            super(owner, initial);
        }

        int limit() {
            return balance + 100;
        }

        void monthEnd() {
            if (balance < 0) {
                balance -= 10;
                log.add("fee 10");
            }
        }
    }

    static class Savings extends Account {
        final int ratePercent;

        Savings(String owner, int initial, int ratePercent) {
            super(owner, initial);
            this.ratePercent = ratePercent;
        }

        int limit() {
            return balance;
        }

        void monthEnd() {
            int interest = balance * ratePercent / 100;
            balance += interest;
            log.add("interest " + interest);
        }
    }

    public static void main(String[] args) {
        Map<String, Account> accounts = new HashMap<>();
        accounts.put("alice", new Checking("alice", 50));
        accounts.put("bob", new Savings("bob", 1000, 3));
        accounts.get("alice").withdraw(120);
        accounts.get("bob").deposit(250);
        accounts.get("bob").withdraw(200);
        for (int month = 0; month < 3; month++) {
            for (Account a : accounts.values()) {
                a.monthEnd();
            }
        }
        for (Account a : accounts.values()) {
            System.out.println(a + " " + a.log);
        }
        accounts.get("bob").withdraw(5000);
    }
}
