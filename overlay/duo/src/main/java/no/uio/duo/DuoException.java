package no.uio.duo;

public class DuoException extends Exception
{
    public DuoException()
    {
        super();
    }

    public DuoException(String s)
    {
        super(s);
    }

    public DuoException(String s, Throwable throwable)
    {
        super(s, throwable);
    }

    public DuoException(Throwable throwable)
    {
        super(throwable);
    }
}
