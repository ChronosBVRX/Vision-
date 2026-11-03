sub Main()
    screen = CreateObject("roSGScreen")
    port = CreateObject("roMessagePort")
    screen.SetMessagePort(port)
    scene = screen.CreateScene("MainScene")
    screen.Show()
    while true
        msg = wait(0, port)
        if Type(msg) = "roSGScreenEvent" and msg.IsScreenClosed()
            exit while
        end if
    end while
end sub
