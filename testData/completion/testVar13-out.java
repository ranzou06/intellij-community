public class Foo {
    Foo f;
    Foo m() {
        Foo foo = m().m().f;
        blah();
        return null;
    }
}