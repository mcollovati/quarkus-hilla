import { ViewConfig } from "@vaadin/hilla-file-router/types.js";
import {Subscription} from "@vaadin/hilla-frontend";
import {useSignal} from "@vaadin/hilla-react-signals";
import {Button} from "@vaadin/react-components"
import * as HelloWorldEndpoint from "Frontend/generated/HelloWorldEndpoint";

export const config: ViewConfig = {
    route: 'push',
    title: 'Push'
}
export default function PushView() {

    const currentTime = useSignal('');
    const clockSubscription = useSignal<Subscription<string> | undefined>(undefined);

    const publicClock = async () => {
        await toggleClock(() => HelloWorldEndpoint.getPublicClock(undefined))
    }

    const publicClockWithLimit = async () => {
        await toggleClock(() => HelloWorldEndpoint.getPublicClock(10))
    }

    const protectedClock = async () => {
        await toggleClock(HelloWorldEndpoint.getClock)
    }

    const subscriptionClock = async () => {
        await toggleClock(HelloWorldEndpoint.getClockCancellable)
    }

    const stopClock = async () => {
        await disconnectClock();
        currentTime.value = '';
    }
    const toggleClock = async (subscriptionFactory: () => Subscription<string>) => {
        await disconnectClock();
        currentTime.value = 'Loading...';
        clockSubscription.value = subscriptionFactory()
            .onNext((msg) => currentTime.value = msg)
            .onError(() => {
                currentTime.value = "Something failed. Maybe you are not authorized?";
                disconnectClock();
            })
            .onComplete(() => {
                currentTime.value = "Bye. Thanks.";
                disconnectClock();
            });
    }

    const disconnectClock = async () => {
        if (clockSubscription.value) {
            clockSubscription.value.cancel();
            clockSubscription.value = undefined;
        }
    }

    return (
        <div className="push-view flex flex-col p-m gap-m items-center">
            <Button id="public"
                    disabled={!!clockSubscription.value}
                    onClick={publicClock}>
                Public
            </Button>
            <Button id="public-limit"
                    disabled={!!clockSubscription.value}
                    onClick={publicClockWithLimit}>
                Public (Limit 10)
            </Button>
            <Button id="protected"
                    disabled={!!clockSubscription.value}
                    onClick={protectedClock}>
                Protected
            </Button>
            <Button id="subscription"
                    disabled={!!clockSubscription.value}
                    onClick={subscriptionClock}>
                EndpointSubscription
            </Button>
            <Button id="stop"
                    disabled={!clockSubscription.value}
                    onClick={stopClock}>
                Stop
            </Button>
            {currentTime.value && <div id="push-contents">{currentTime.value}</div>}
        </div>

    )
}