package io.spill.norm

import io.spill.ReturnAction.{ ReturnColumns, ReturnRecord }
import io.spill._
import io.spill.ast.Renameable.ByStrategy
import io.spill.ast.Visibility.Visible
import io.spill.ast._
import io.spill.context.Expand

class ExpandReturningSpec extends Spec {

  case class Person(name: String, age: Int)
  case class Foo(bar: String, baz: Int)

  "inner apply" - {
    val mi = MirrorIdiom
    val ctx = new MirrorContext(mi, Literal)
    import ctx._

    "should replace tuple clauses with ExternalIdent" in {
      val q = quote {
        stream[Person]
          .insert(lift(Person("Joe", 123)))
          .returning(p => (p.name, p.age))
      }
      val list =
        ExpandReturning.apply(q.ast.asInstanceOf[Returning])(
          MirrorIdiom,
          Literal
        )
      list must matchPattern {
        case List(
          (Property(ExternalIdent("p"), "name"), _),
          (Property(ExternalIdent("p"), "age"), _)
          ) =>
      }
    }

    "should replace case class clauses with ExternalIdent" in {
      val q = quote {
        stream[Person]
          .insert(lift(Person("Joe", 123)))
          .returning(p => Foo(p.name, p.age))
      }
      val list =
        ExpandReturning.apply(q.ast.asInstanceOf[Returning])(
          MirrorIdiom,
          Literal
        )
      list must matchPattern {
        case List(
          (Property(ExternalIdent("p"), "name"), _),
          (Property(ExternalIdent("p"), "age"), _)
          ) =>
      }
    }
  }

  "returning clause" - {
    val mi = MirrorIdiom
    val ctx = new MirrorContext(mi, Literal)
    import ctx._
    val q = quote { stream[Person].insert(lift(Person("Joe", 123))) }

    "should expand tuples with plain record" in {
      val qi = quote { q.returning(p => (p.name, p.age)) }
      val ret =
        ExpandReturning.applyMap(qi.ast.asInstanceOf[Returning]) {
          case (ast, stmt) =>
            fail("Should not use this method for the returning clause")
        }(mi, Literal)

      ret mustBe ReturnRecord
    }
    "should expand case classes with plain record" in {
      val qi = quote { q.returning(p => Foo(p.name, p.age)) }
      val ret =
        ExpandReturning.applyMap(qi.ast.asInstanceOf[Returning]) {
          case (ast, stmt) =>
            fail("Should not use this method for the returning clause")
        }(mi, Literal)

      ret mustBe ReturnRecord
    }
    "should expand whole record with plain record (converted to tuple in parser)" in {
      val qi = quote { q.returning(p => p) }
      val ret =
        ExpandReturning.applyMap(qi.ast.asInstanceOf[Returning]) {
          case (ast, stmt) =>
            fail("Should not use this method for the returning clause")
        }(mi, Literal)

      ret mustBe ReturnRecord
    }
  }

  "returning multi" - {
    val mi = MirrorIdiomReturningMulti
    val ctx = new MirrorContext(mi, Literal)
    import ctx._
    val q = quote { stream[Person].insert(lift(Person("Joe", 123))) }

    "should expand tuples" in {
      val qi = quote { q.returning(p => (p.name, p.age)) }
      val ret =
        ExpandReturning.applyMap(qi.ast.asInstanceOf[Returning]) {
          case (ast, stmt) => Expand(ctx, ast, stmt, mi, Literal).string
        }(mi, Literal)
      ret mustBe ReturnColumns(List("name", "age"))
    }
    "should expand case classes" in {
      val qi = quote { q.returning(p => Foo(p.name, p.age)) }
      val ret =
        ExpandReturning.applyMap(qi.ast.asInstanceOf[Returning]) {
          case (ast, stmt) => Expand(ctx, ast, stmt, mi, Literal).string
        }(mi, Literal)
      ret mustBe ReturnColumns(List("name", "age"))
    }
    "should expand case classes (converted to tuple in parser)" in {
      val qi = quote { q.returning(p => p) }
      val ret =
        ExpandReturning.applyMap(qi.ast.asInstanceOf[Returning]) {
          case (ast, stmt) => Expand(ctx, ast, stmt, mi, Literal).string
        }(mi, Literal)
      ret mustBe ReturnColumns(List("name", "age"))
    }
  }

  "returning single and unsupported" - {

    val renameable = ByStrategy

    def insert = Insert(
      Map(
        Entity.Opinionated("Person", List(), renameable),
        Ident("p"),
        Tuple(
          List(
            Property.Opinionated(Ident("p"), "name", renameable, Visible),
            Property.Opinionated(Ident("p"), "age", renameable, Visible)
          )
        )
      ),
      List(
        Assignment(
          Ident("pp"),
          Property.Opinionated(Ident("pp"), "name", renameable, Visible),
          Constant("Joe")
        )
      )
    )
    def retMulti =
      Returning(
        insert,
        Ident("r"),
        Tuple(
          List(
            Property.Opinionated(Ident("r"), "name", renameable, Visible),
            Property.Opinionated(Ident("r"), "age", renameable, Visible)
          )
        )
      )
    def retSingle =
      Returning(
        insert,
        Ident("r"),
        Tuple(
          List(Property.Opinionated(Ident("r"), "name", renameable, Visible))
        )
      )

    "returning single" - {
      val mi = MirrorIdiomReturningSingle
      val ctx = new MirrorContext(mi, Literal)

      "should fail if multiple fields encountered" in {
        assertThrows[IllegalArgumentException] {
          ExpandReturning.applyMap(retMulti) {
            case (ast, stmt) => Expand(ctx, ast, stmt, mi, Literal).string
          }(mi, Literal)
        }
      }
      "should succeed if single field encountered" in {
        val ret =
          ExpandReturning.applyMap(retSingle) {
            case (ast, stmt) => Expand(ctx, ast, stmt, mi, Literal).string
          }(mi, Literal)
        ret mustBe ReturnColumns(List("name"))
      }
    }
    "returning unsupported" - {
      val mi = MirrorIdiomReturningUnsupported
      val ctx = new MirrorContext(mi, Literal)

      "should fail if multiple fields encountered" in {
        assertThrows[IllegalArgumentException] {
          ExpandReturning.applyMap(retMulti) {
            case (ast, stmt) => Expand(ctx, ast, stmt, mi, Literal).string
          }(mi, Literal)
        }
      }
      "should fail if single field encountered" in {
        assertThrows[IllegalArgumentException] {
          ExpandReturning.applyMap(retSingle) {
            case (ast, stmt) => Expand(ctx, ast, stmt, mi, Literal).string
          }(mi, Literal)
        }
      }
    }
  }
}
