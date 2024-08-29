import {Button, Notification, TextField} from "@vaadin/react-components";
import React, {useState} from "react";
import {HelloWorldEndpoint} from "Frontend/generated/endpoints";
import UserPOJO
    from "Frontend/generated/com/example/application/entities/UserPOJO";
import { useSignal } from "@vaadin/hilla-react-signals";
import { Subscription } from "@vaadin/hilla-frontend";
import { ViewConfig } from "@vaadin/hilla-file-router/types.js";

export const config: ViewConfig = {
    title: 'Home',
    menu: { exclude: true },
    route: 'home'
}

export default function HelloWorldView() {
    const [name, setName] = useState('');
    const serverTime = useSignal('');
    const clockSubscription = useSignal<Subscription<string> | undefined>(undefined);


    const sayHello = async () => {
        const serverResponse = await HelloWorldEndpoint.sayHello2(name)
        Notification.show(serverResponse);
    }
    const sayComplexHello = async () => {
        const pojo: UserPOJO = {
            name: name,
            surname: "surname"
        }
        const serverResponse = await HelloWorldEndpoint.sayComplexHello(pojo);
        Notification.show(serverResponse);
    }

    const sayHelloProtected = () => {
        HelloWorldEndpoint.sayHelloProtected()
            .catch( (err) => err)
            .then( (msg) => Notification.show(msg) );
    }

    const toggleClock = async () => {
        if (clockSubscription.value) {
            clockSubscription.value.cancel();
            clockSubscription.value = undefined;
            serverTime.value = '';
        } else {
            serverTime.value = 'Loading...';
            clockSubscription.value = HelloWorldEndpoint.getClockCancellable()
                .onNext((msg) => serverTime.value = msg)
                .onError(() => serverTime.value = "Something failed. Maybe you are not authorized?")
                .onComplete(() => serverTime.value = "Bye. Thanks.");
        }
    }

    return (
        <div className="home-view">
            <TextField label="Your name"
                       onValueChanged={ev => setName(ev.detail.value)}></TextField>
            <Button onClick={sayHello}>Say hello</Button>
            <Button onClick={sayComplexHello}>Say complex hello</Button>
            <Button onClick={sayHelloProtected}>Say protected hello</Button>
            <Button onClick={toggleClock}>Toggle clock</Button>
            {serverTime.value && <div>{serverTime.value}</div> }
        </div>
    );
}