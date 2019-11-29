package contarticle

// This article is supposed to be read from start to end.
object Domain {
	// Domain models
	case class User(id: Long)
	case class Info(name: String)

	// Data storage:
	val users = Map(123L -> User(123))
	val info = Map(123L -> Info("Tom"))

	// Access functions
	def getUser(id: Long): User = {
		users.getOrElse(id, null)
	}

	def getInfo(user: User): Info = {
		info.getOrElse(user.id, null)
	}
}

