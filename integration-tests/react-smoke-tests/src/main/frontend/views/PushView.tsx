/*
 * Copyright 2024 Marco Collovati, Dario Götze
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import {ViewConfig} from "@vaadin/hilla-file-router/types.js";
import {Subscription} from "@vaadin/hilla-frontend";
import {useSignal} from "@vaadin/hilla-react-signals";
import {Button} from "@vaadin/react-components/Button.js";
import {ClockService} from "Frontend/generated/endpoints";

export const config: ViewConfig = {
    title: "Push View",
    route: "push"
}

export default function PushView() {

    const subscription = useSignal<Subscription<string> | undefined>(undefined);
    const currentTime = useSignal<string>("");

    const cancelClock = async () => {
        if (subscription.value) {
            subscription.value.cancel();
        }
        subscription.value = undefined;
    }
    const toggleClock = async (factory: () => Subscription<string>) => {
        await cancelClock();
        currentTime.value = "Loading...";
        subscription.value = factory()
            .onNext((msg) => currentTime.value = msg)
            .onError(() => {
                currentTime.value = "Something failed. Maybe you are not authorized?";
                cancelClock();
            })
            .onComplete(() => {
                currentTime.value = "Bye. Thanks.";
                cancelClock()
            });
    }

    return <>
        <div id="push-view" className="flex flex-col p-m gap-m items-center">
            <Button id="public"
                    onClick={(e) =>
                        toggleClock(() => ClockService.getPublicClock(undefined))
                    }
                    disabled={!!subscription.value}>Public</Button>
            <Button id="public-limit"
                    onClick={(e) =>
                        toggleClock(() => ClockService.getPublicClock(10))
                    }
                    disabled={!!subscription.value}>Public (Limit 10)</Button>
            <Button id="protected"
                    onClick={(e) =>
                        toggleClock(ClockService.getClock)
                    }
                    disabled={!!subscription.value}>Protected</Button>
            <Button id="subscription"
                    onClick={(e) =>
                        toggleClock(ClockService.getClockCancellable)
                    }
                    disabled={!!subscription.value}>EndpointSubscription</Button>
            <Button id="stop" onClick={(e) => {
                cancelClock().then(() => currentTime.value = "");
            }} disabled={!subscription.value}>Stop</Button>
        </div>
        {currentTime.value ?
            <div id="push-contents">{currentTime.value}</div>
            : ''
        }
    </>
}