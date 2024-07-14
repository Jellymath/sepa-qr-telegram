import java.sql.Connection

//language=sqlite
const val createTable = """
CREATE TABLE IF NOT EXISTS accounts (
    user_id INTEGER,
    alias TEXT,
    iban TEXT,
    name TEXT
)
"""

//language=sqlite
const val getAccountsQuery = "SELECT alias, iban, name FROM accounts WHERE user_id = ?"

//language=sqlite
const val getAliasesQuery = "SELECT alias FROM accounts WHERE user_id = ?"

//language=sqlite
const val addAccountQuery = "INSERT INTO accounts (user_id, alias, iban, name) VALUES (?, ?, ?, ?)"

//language=sqlite
const val deleteAccountQuery = "DELETE FROM accounts WHERE user_id = ? AND alias = ?"

//language=sqlite
const val deleteAllAccountsQuery = "DELETE FROM accounts WHERE user_id = ?"

fun Connection.createAccountsTable() {
    prepareStatement(createTable).execute()
}

fun Connection.getAccounts(userId: Long): Set<Account> {
    return prepareStatement(getAccountsQuery).use { statement ->
        statement.setLong(1, userId)
        statement.executeQuery().use { resultSet ->
            generateSequence {
                if (resultSet.next()) {
                    Account(
                        resultSet.getString("alias"),
                        resultSet.getString("iban"),
                        resultSet.getString("name")
                    )
                } else null
            }.toSet()
        }
    }
}

fun Connection.getAliases(userId: Long): Set<String> {
    return prepareStatement(getAliasesQuery).use { statement ->
        statement.setLong(1, userId)
        statement.executeQuery().use { resultSet ->
            generateSequence {
                if (resultSet.next()) {
                    resultSet.getString("alias")
                } else null
            }.toSet()
        }
    }
}

fun Connection.addAccount(userId: Long, account: Account) {
    prepareStatement(addAccountQuery).use { statement ->
        statement.setLong(1, userId)
        statement.setString(2, account.alias)
        statement.setString(3, account.iban)
        statement.setString(4, account.name)
        statement.execute()
    }
}

fun Connection.deleteAccount(userId: Long, alias: String) {
    prepareStatement(deleteAccountQuery).use { statement ->
        statement.setLong(1, userId)
        statement.setString(2, alias)
        statement.execute()
    }
}

fun Connection.deleteAllAccounts(userId: Long) {
    prepareStatement(deleteAllAccountsQuery).use { statement ->
        statement.setLong(1, userId)
        statement.execute()
    }
}